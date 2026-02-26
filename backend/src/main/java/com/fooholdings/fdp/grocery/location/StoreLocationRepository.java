package com.fooholdings.fdp.grocery.location;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreLocationRepository extends JpaRepository<StoreLocationEntity, String> {
    
}