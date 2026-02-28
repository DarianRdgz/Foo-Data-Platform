package com.fooholdings.fdp.core.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionRunRepository extends JpaRepository<IngestionRunEntity, UUID> {
}
