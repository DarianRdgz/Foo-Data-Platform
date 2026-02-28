package com.fooholdings.fdp.core.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for fdp_core.ingestion_run.
 *
 * JSONB handling: @JdbcTypeCode(SqlTypes.JSON) tells Hibernate 7 to map the
 * Java String to the Postgres jsonb column type correctly.
 */
@Entity
@Table(schema = "fdp_core", name = "ingestion_run")
public class IngestionRunEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "source_system_id", nullable = false)
    private short sourceSystemId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** RUNNING | SUCCESS | FAILED | PARTIAL */
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "message")
    private String message;

    @Column(name = "requested_scope_json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String requestedScopeJson;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by")
    private String lockedBy;

    // Getters / Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public short getSourceSystemId() { return sourceSystemId; }
    public void setSourceSystemId(short sourceSystemId) { this.sourceSystemId = sourceSystemId; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getRequestedScopeJson() { return requestedScopeJson; }
    public void setRequestedScopeJson(String requestedScopeJson) { this.requestedScopeJson = requestedScopeJson; }

    public Instant getLockedAt() { return lockedAt; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }

    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }
}
