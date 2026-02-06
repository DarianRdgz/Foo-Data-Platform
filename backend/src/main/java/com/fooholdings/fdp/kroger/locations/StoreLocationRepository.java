package com.fooholdings.fdp.kroger.locations;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreLocationRepository extends JpaRepository<StoreLocationEntity, String> {
    
}