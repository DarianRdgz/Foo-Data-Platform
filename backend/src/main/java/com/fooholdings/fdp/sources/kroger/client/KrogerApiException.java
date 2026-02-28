package com.fooholdings.fdp.sources.kroger.client;

/**
 * Unchecked exception for Kroger API failures (HTTP errors, parse failures).
 *
 * Caught by ingestion services to trigger run FAILED status.
 * Raw payloads are saved before
 */
public class KrogerApiException extends RuntimeException {

    public KrogerApiException(String message) {
        super(message);
    }

    public KrogerApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
