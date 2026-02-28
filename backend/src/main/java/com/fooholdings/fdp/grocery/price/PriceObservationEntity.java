package com.fooholdings.fdp.grocery.price;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for fdp_grocery.price_observation.
 *
 * All writes go through PriceObservationJdbcRepository using
 * "INSERT ... ON CONFLICT DO NOTHING" for dedup-safe batch ingestion.
 *
 * The primary key is a BIGSERIAL
 */
@Entity
@Table(schema = "fdp_grocery", name = "price_observation")
public class PriceObservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "source_system_id", nullable = false)
    private short sourceSystemId;

    @Column(name = "store_location_id", nullable = false)
    private UUID storeLocationId;

    @Column(name = "source_product_pk", nullable = false)
    private UUID sourceProductPk;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "USD";

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "regular_price", precision = 10, scale = 2)
    private BigDecimal regularPrice;

    @Column(name = "promo_price", precision = 10, scale = 2)
    private BigDecimal promoPrice;

    @Column(name = "is_on_sale")
    private Boolean isOnSale;

    @Column(name = "ingestion_run_id")
    private UUID ingestionRunId;

    @Column(name = "raw_payload_id")
    private UUID rawPayloadId;

    // Getters

    public Long getId() { return id; }
    public short getSourceSystemId() { return sourceSystemId; }
    public UUID getStoreLocationId() { return storeLocationId; }
    public UUID getSourceProductPk() { return sourceProductPk; }
    public Instant getObservedAt() { return observedAt; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getRegularPrice() { return regularPrice; }
    public BigDecimal getPromoPrice() { return promoPrice; }
    public Boolean getIsOnSale() { return isOnSale; }
    public UUID getIngestionRunId() { return ingestionRunId; }
    public UUID getRawPayloadId() { return rawPayloadId; }

    // Setters for JPA hydration only
    public void setId(Long id) { this.id = id; }
    public void setSourceSystemId(short sourceSystemId) { this.sourceSystemId = sourceSystemId; }
    public void setStoreLocationId(UUID storeLocationId) { this.storeLocationId = storeLocationId; }
    public void setSourceProductPk(UUID sourceProductPk) { this.sourceProductPk = sourceProductPk; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setRegularPrice(BigDecimal regularPrice) { this.regularPrice = regularPrice; }
    public void setPromoPrice(BigDecimal promoPrice) { this.promoPrice = promoPrice; }
    public void setIsOnSale(Boolean isOnSale) { this.isOnSale = isOnSale; }
    public void setIngestionRunId(UUID ingestionRunId) { this.ingestionRunId = ingestionRunId; }
    public void setRawPayloadId(UUID rawPayloadId) { this.rawPayloadId = rawPayloadId; }
}
