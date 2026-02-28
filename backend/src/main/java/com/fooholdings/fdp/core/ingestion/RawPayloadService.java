package com.fooholdings.fdp.core.ingestion;

import java.sql.SQLException;
import java.util.UUID;

import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.core.source.SourceSystemService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Archives raw API response payloads to fdp_core.raw_payload.
 *
 * Every API call result — success or failure — is stored here before parsing.
 * This is the audit trail and the "source of truth" for debugging. The raw_payload_id
 * is threaded through to price_observation so any observation can be traced back
 * to the exact API response that produced it.
 *
 * JSONB implementation note:
 *   Ro signal the correct JDBC type to the driver:
 *
 *     PGobject obj = new PGobject();
 *     obj.setType("jsonb");
 *     obj.setValue(json);
 *
 */
@Service
public class RawPayloadService {

    private static final Logger log = LoggerFactory.getLogger(RawPayloadService.class);
    private static final String FALLBACK_PAYLOAD = "{\"_fdp_error\":\"payload_was_null_or_empty\"}";

    private final JdbcTemplate jdbc;
    private final SourceSystemService sourceSystemService;
    private final ObjectMapper mapper;

    public RawPayloadService(JdbcTemplate jdbc,
                             SourceSystemService sourceSystemService,
                             ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.sourceSystemService = sourceSystemService;
        this.mapper = mapper;
    }

    /**
     * Saves a raw API response payload and returns the new raw_payload row's UUID.
     *
     * @param sourceCode
     * @param endpoint
     * @param responseStatus
     * @param rawBody
     * @param ingestionRunId
     * @return
     */
    public UUID save(String sourceCode, String endpoint, int responseStatus,
                     String rawBody, UUID ingestionRunId) {
        short sourceSystemId = sourceSystemService.getRequiredIdByCode(sourceCode);
        UUID id = UUID.randomUUID();

        String payloadJson = toValidJson(rawBody);
        PGobject jsonbObject = buildJsonbObject(payloadJson);

        jdbc.update("""
                INSERT INTO fdp_core.raw_payload
                  (id, source_system_id, endpoint, requested_at, response_status, payload_json, ingestion_run_id)
                VALUES
                  (?, ?, ?, now(), ?, ?, ?)
                """,
                id, sourceSystemId, endpoint, responseStatus, jsonbObject, ingestionRunId
        );

        log.debug("[payload:{}] Saved — source={}, endpoint={}, status={}", id, sourceCode, endpoint, responseStatus);
        return id;
    }

    // Helpers

    //Validates that the string is parseable JSON. Returns a fallback object if not
    private String toValidJson(String raw) {
        if (raw == null || raw.isBlank()) return FALLBACK_PAYLOAD;
        try {
            // Validate and re-serialize to canonical form
            mapper.readTree(raw);
            return raw;
        } catch (JacksonException e) {
            log.warn("Raw payload is not valid JSON — storing fallback. Preview: {}",
                    raw.length() > 200 ? raw.substring(0, 200) + "..." : raw);
            return FALLBACK_PAYLOAD;
        }
    }


    //Wraps a JSON string in a PGobject typed as jsonb
    private static PGobject buildJsonbObject(String json) {
        try {
            PGobject obj = new PGobject();
            obj.setType("jsonb");
            obj.setValue(json);
            return obj;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to construct PGobject for jsonb — this should not happen", e);
        }
    }
}
