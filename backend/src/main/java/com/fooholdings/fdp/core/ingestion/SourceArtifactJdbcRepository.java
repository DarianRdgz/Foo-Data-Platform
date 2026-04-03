package com.fooholdings.fdp.core.ingestion;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fooholdings.fdp.core.source.SourceSystemService;

@Repository
public class SourceArtifactJdbcRepository {

    private final JdbcTemplate jdbc;
    private final SourceSystemService sourceSystemService;

    public SourceArtifactJdbcRepository(JdbcTemplate jdbc, SourceSystemService sourceSystemService) {
        this.jdbc = jdbc;
        this.sourceSystemService = sourceSystemService;
    }

    public boolean existsBySha256(String sourceCode, String sha256) {
        short sourceSystemId = sourceSystemService.getRequiredIdByCode(sourceCode);
        Integer count = jdbc.queryForObject(
                """
                select count(*)
                from fdp_core.source_artifact
                where source_system_id = ?
                  and sha256 = ?
                """,
                Integer.class,
                sourceSystemId, sha256
        );
        return count != null && count > 0;
    }

    public void insertDiscovered(
            String sourceCode,
            String artifactType,
            String collectionCode,
            String stateCode,
            Integer artifactYear,
            String originalFilename,
            Path storagePath,
            String sourceUrl,
            String sha256,
            long fileSizeBytes
    ) {
        short sourceSystemId = sourceSystemService.getRequiredIdByCode(sourceCode);
        UUID id = UUID.randomUUID();

        jdbc.update(
                """
                insert into fdp_core.source_artifact
                  (id, source_system_id, artifact_type, collection_code, state_code, artifact_year,
                   original_filename, storage_path, source_url, sha256, file_size_bytes, status)
                values
                  (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DOWNLOADED')
                on conflict do nothing
                """,
                id, sourceSystemId, artifactType, collectionCode, stateCode, artifactYear,
                originalFilename, storagePath.toString(), sourceUrl, sha256, fileSizeBytes
        );
    }

    public List<TrackedArtifact> findPendingBySourceCode(String sourceCode) {
        short sourceSystemId = sourceSystemService.getRequiredIdByCode(sourceCode);
        return jdbc.query(
                """
                select id, collection_code, state_code, artifact_year, original_filename, storage_path
                from fdp_core.source_artifact
                where source_system_id = ?
                  and status = 'DOWNLOADED'
                order by downloaded_at asc
                """,
                (rs, rowNum) -> mapTrackedArtifact(rs),
                sourceSystemId
        );
    }

    public void markIngested(UUID artifactId, UUID ingestionRunId) {
        jdbc.update(
                """
                update fdp_core.source_artifact
                set status = 'INGESTED',
                    processed_at = now(),
                    ingestion_run_id = ?,
                    notes = null
                where id = ?
                """,
                ingestionRunId, artifactId
        );
    }

    public void markFailed(UUID artifactId, UUID ingestionRunId, String notes) {
        jdbc.update(
                """
                update fdp_core.source_artifact
                set status = 'FAILED',
                    processed_at = now(),
                    ingestion_run_id = ?,
                    notes = ?
                where id = ?
                """,
                ingestionRunId, truncate(notes), artifactId
        );
    }

    private TrackedArtifact mapTrackedArtifact(ResultSet rs) throws SQLException {
        return new TrackedArtifact(
                UUID.fromString(rs.getString("id")),
                rs.getString("collection_code"),
                rs.getString("state_code"),
                (Integer) rs.getObject("artifact_year"),
                rs.getString("original_filename"),
                Path.of(rs.getString("storage_path"))
        );
    }

    private String truncate(String notes) {
        if (notes == null) {
            return null;
        }
        return notes.length() <= 2000 ? notes : notes.substring(0, 2000);
    }

    public record TrackedArtifact(
            UUID id,
            String collectionCode,
            String stateCode,
            Integer artifactYear,
            String originalFilename,
            Path storagePath
    ) {}
}
