package com.fooholdings.fdp.grocery.product;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "fdp_grocery", name = "source_product")
public class SourceProductEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "source_system_id", nullable = false)
    private short sourceSystemId;

    @Column(name = "source_product_id", nullable = false)
    private String sourceProductId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "upc")
    private String upc;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "brand")
    private String brand;

    @Column(name = "product_page_uri")
    private String productPageUri;

    @Column(name = "raw_category_json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String rawCategoryJson;

    @Column(name = "raw_flags_json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String rawFlagsJson;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    // Getters / Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public short getSourceSystemId() { return sourceSystemId; }
    public void setSourceSystemId(short sourceSystemId) { this.sourceSystemId = sourceSystemId; }

    public String getSourceProductId() { return sourceProductId; }
    public void setSourceProductId(String sourceProductId) { this.sourceProductId = sourceProductId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public String getUpc() { return upc; }
    public void setUpc(String upc) { this.upc = upc; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getProductPageUri() { return productPageUri; }
    public void setProductPageUri(String productPageUri) { this.productPageUri = productPageUri; }

    public String getRawCategoryJson() { return rawCategoryJson; }
    public void setRawCategoryJson(String rawCategoryJson) { this.rawCategoryJson = rawCategoryJson; }

    public String getRawFlagsJson() { return rawFlagsJson; }
    public void setRawFlagsJson(String rawFlagsJson) { this.rawFlagsJson = rawFlagsJson; }

    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
