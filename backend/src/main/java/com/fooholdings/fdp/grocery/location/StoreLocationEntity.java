package com.fooholdings.fdp.grocery.location;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "fdp_grocery", name = "store_location")
public class StoreLocationEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "source_system_id", nullable = false)
    private short sourceSystemId;

    @Column(name = "source_location_id", nullable = false)
    private String sourceLocationId;

    @Column(name = "chain_code")
    private String chainCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone")
    private String phone;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "city")
    private String city;

    @Column(name = "state_code", length = 2)
    private String stateCode;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "geo_region_id")
    private Long geoRegionId;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    // Getters / Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public short getSourceSystemId() { return sourceSystemId; }
    public void setSourceSystemId(short sourceSystemId) { this.sourceSystemId = sourceSystemId; }

    public String getSourceLocationId() { return sourceLocationId; }
    public void setSourceLocationId(String sourceLocationId) { this.sourceLocationId = sourceLocationId; }

    public String getChainCode() { return chainCode; }
    public void setChainCode(String chainCode) { this.chainCode = chainCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public Long getGeoRegionId() { return geoRegionId; }
    public void setGeoRegionId(Long geoRegionId) { this.geoRegionId = geoRegionId; }

    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
