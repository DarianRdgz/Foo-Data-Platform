package com.fooholdings.fdp.grocery.product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for fdp_grocery.source_product
 * Read path only
 */
public interface SourceProductRepository extends JpaRepository<SourceProductEntity, UUID> {

    Optional<SourceProductEntity> findBySourceSystemIdAndSourceProductId(
            short sourceSystemId, String sourceProductId);

    List<SourceProductEntity> findByNameContainingIgnoreCase(String q, Pageable pageable);

    List<SourceProductEntity> findBySourceSystemIdAndNameContainingIgnoreCase(
            short sourceSystemId,
            String q,
            Pageable pageable
    );

    List<SourceProductEntity> findByUpc(String upc, Pageable pageable);

    List<SourceProductEntity> findBySourceSystemIdAndUpc(
            short sourceSystemId,
            String upc,
            Pageable pageable
    );
}