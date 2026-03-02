package com.fooholdings.fdp.grocery.location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for fdp_grocery.store_location.
 *
 * Reads go through this interface. Writes use StoreLocationJdbcRepository
 * for upsert support *warning* JPA need custom queries for ON CONFLICT DO UPDATE
 */
public interface StoreLocationRepository extends JpaRepository<StoreLocationEntity, UUID> {

    Optional<StoreLocationEntity> findBySourceSystemIdAndSourceLocationId(
            short sourceSystemId, String sourceLocationId);

    List<StoreLocationEntity> findTop200ByPostalCodeOrderByNameAsc(String postalCode);

    List<StoreLocationEntity> findTop200BySourceSystemIdAndPostalCodeOrderByNameAsc(
            short sourceSystemId,
            String postalCode
    );
}