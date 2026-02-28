package com.fooholdings.fdp.grocery.product;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for fdp_grocery.source_product
 * Read path only
 */
public interface SourceProductRepository extends JpaRepository<SourceProductEntity, UUID> {

    Optional<SourceProductEntity> findBySourceSystemIdAndSourceProductId(
            short sourceSystemId, String sourceProductId);
}
