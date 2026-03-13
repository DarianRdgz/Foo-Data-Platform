package com.fooholdings.fdp.geo.support;

/**
 * Thrown when a requested geo area does not exist in fdp_geo.geo_areas.
 * Mapped to HTTP 404 by ApiExceptionHandler.
 */
public class GeoAreaNotFoundException extends RuntimeException {

    public GeoAreaNotFoundException(String geoLevel, String geoId) {
        super("No geo area found for level='" + geoLevel + "' id='" + geoId + "'");
    }
}