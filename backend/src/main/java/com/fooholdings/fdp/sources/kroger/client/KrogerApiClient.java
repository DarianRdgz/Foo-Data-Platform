package com.fooholdings.fdp.sources.kroger.client;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fooholdings.fdp.core.ingestion.ApiQuotaUsageService;
import com.fooholdings.fdp.core.ingestion.RawPayloadService;
import com.fooholdings.fdp.core.logging.MdcScope;
import com.fooholdings.fdp.sources.kroger.auth.KrogerTokenService;
import com.fooholdings.fdp.sources.kroger.config.KrogerProperties;
import com.fooholdings.fdp.sources.kroger.dto.locations.KrogerLocationResponse;
import com.fooholdings.fdp.sources.kroger.dto.products.KrogerProductsResponse;

/**
 * HTTP client adapter for the Kroger v1 API.
 *
 * Responsibilities:
 *   - Build authorized requests with Bearer token
 *   - Save EVERY response (success or failure) to fdp_core.raw_payload
 *   - Increment api_quota_usage on every call
 *   - Handle 401 with one token force-refresh + retry
 *
 * Key design decision — raw payload on error:
 *   RestClient.retrieve() throws by default on 4xx/5xx, discarding the body.
 *   We suppress the default error handler with .onStatus(any, no-op) to capture
 *   the full body first, then throw manually if status is an error.
 *   This ensures error responses are archived.
 *
 * 401 retry policy:
 *   One retry after force-refresh. If the second attempt also returns 401,
 *   we throw the credentials are invalid and retrying is pointless.
 */
@Component
public class KrogerApiClient {

    private static final Logger log = LoggerFactory.getLogger(KrogerApiClient.class);
    private static final String SOURCE_CODE = "KROGER";

    private final RestClient restClient;
    private final KrogerTokenService tokenService;
    private final RawPayloadService rawPayloadService;
    private final ApiQuotaUsageService quotaUsageService;
    private final JsonMapper objectMapper;

    public KrogerApiClient(RestClient.Builder builder,
                           KrogerProperties props,
                           KrogerTokenService tokenService,
                           RawPayloadService rawPayloadService,
                           ApiQuotaUsageService quotaUsageService,
                           JsonMapper objectMapper) {
        this.tokenService = tokenService;
        this.rawPayloadService = rawPayloadService;
        this.quotaUsageService = quotaUsageService;
        this.objectMapper = objectMapper;
        this.restClient = builder
                .baseUrl(props.getApi().getBaseUrl())
                .build();
    }

    // Public API methods

    /**
     * Fetches store locations filtered by zip code.
     *
     * @param zipCode        5-digit zip to filter by
     * @param ingestionRunId current run for audit linkage
     * @return parsed response
     */
    public KrogerLocationResponse getLocations(String zipCode, UUID ingestionRunId) {
        String endpoint = "GET /locations";
        String url = "/locations?filter.zipCode.near=" + zipCode + "&filter.limit=10";
        String body = executeGet(url, endpoint, ingestionRunId, false);
        return parseBody(body, KrogerLocationResponse.class, endpoint);
    }

    /**
     * Fetches products for a given location.
     *
     * @param locationId     Kroger location ID (from store_location.source_location_id)
     * @param term           search term (e.g. "milk")
     * @param ingestionRunId current run for audit linkage
     * @return parsed response
     */
    public KrogerProductsResponse getProducts(String locationId, String term, UUID ingestionRunId) {
        String endpoint = "GET /products";
        String url = "/products?filter.locationId=" + locationId + "&filter.term=" + term + "&filter.limit=50";
        String body = executeGet(url, endpoint, ingestionRunId, false);
        return parseBody(body, KrogerProductsResponse.class, endpoint);
    }

    // Internal HTTP execution

    /**
     * Executes a GET request with Authorization header.
     * Saves the raw payload on every response. Handles 401 with one retry.
     *
     * @param retrying true when this is the second attempt after a 401
     */
    private String executeGet(String url, String endpoint, UUID ingestionRunId, boolean retrying) {
        try (@SuppressWarnings("unused") var ignored = MdcScope.with("endpoint", endpoint)) {
            String token = retrying ? tokenService.forceRefresh() : tokenService.getValidToken();

            quotaUsageService.increment(SOURCE_CODE, endpoint, 1);

            ResponseEntity<String> response = restClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .onStatus(status -> true, (req, res) -> {})
                    .toEntity(String.class);

            int statusCode = response.getStatusCode().value();
            String body = response.getBody();

            rawPayloadService.save(SOURCE_CODE, endpoint, statusCode, body, ingestionRunId);

            if (statusCode == HttpStatus.UNAUTHORIZED.value() && !retrying) {
                log.warn("Kroger returned 401 - forcing token refresh and retrying once");
                return executeGet(url, endpoint, ingestionRunId, true);
            }

            if (response.getStatusCode().isError()) {
                throw new KrogerApiException(
                    "Kroger API returned HTTP " + statusCode + " for " + endpoint +
                    " - body saved to raw_payload for run " + ingestionRunId
                );
            }

            return body;
        }
    }


    private <T> T parseBody(String body, Class<T> type, String endpoint) {
        if (body == null || body.isBlank()) {
            throw new KrogerApiException("Empty response body from Kroger endpoint: " + endpoint);
        }
        try {
            return objectMapper.readValue(body, type);
        } catch (JacksonException e) {
            throw new KrogerApiException("Failed to parse Kroger response for " + endpoint + ": " + e.getMessage(), e);
        }
    }
}
