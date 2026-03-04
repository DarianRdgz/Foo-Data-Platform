package com.fooholdings.fdp.core.ingestion;

public class IngestionLockException extends RuntimeException {
    public IngestionLockException(String message) {
        super(message);
    }
}