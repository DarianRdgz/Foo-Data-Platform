package com.fooholdings.fdp.sources.model;

/**
 * Enumerates supported upstream data sources.
 *
 * IMPORTANT:
 * - Nothing in this package may depend on any particular source implementation.
 */
public enum SourceType {

    // Kroger public API. Must match fdp_core.source_system.code
    KROGER("KROGER", "Kroger");

    private final String code;
    private final String displayName;

    SourceType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    // Must match fdp_core.source_system.code
    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }
}