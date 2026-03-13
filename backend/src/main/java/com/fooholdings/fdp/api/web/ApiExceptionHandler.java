package com.fooholdings.fdp.api.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fooholdings.fdp.core.ingestion.IngestionLockException;
import com.fooholdings.fdp.core.logging.ErrorCategory;
import com.fooholdings.fdp.core.logging.ErrorCategoryMdc;
import com.fooholdings.fdp.geo.support.GeoAreaNotFoundException;
import com.fooholdings.fdp.sources.kroger.client.KrogerApiException;

/**
 * Global exception handler for all REST controllers.
 *
 * Classifies each exception with a structured error_category MDC field,
 * logs at the appropriate level, and returns a consistent JSON error body.
 *
 * Stack trace policy: only ERROR-level logs carry a stack trace (enforced by
 * the logback-spring.xml appender split). WARN-level calls never pass the
 * throwable to the logger.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Handles @Valid failures on request bodies.
     * HTTP 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException ex) {
        try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.VALIDATION_ERROR)) {
            log.warn("Request validation failed: {}", ex.getMessage());
        }
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return ResponseEntity.badRequest()
                .body(new ErrorBody("VALIDATION_ERROR", detail));
    }

    /**
     * Handles distributed lock contention, a normal operational condition.
     * HTTP 409 Conflict
     */
    @ExceptionHandler(IngestionLockException.class)
    public ResponseEntity<ErrorBody> lock(IngestionLockException ex) {
        try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.LOCK_ERROR)) {
            log.warn("Ingestion lock contention: {}", ex.getMessage());
        }
        return ResponseEntity.status(409)
                .body(new ErrorBody("LOCKED", ex.getMessage()));
    }

    /**
     * Handles Kroger API failures (HTTP errors, parse failures, timeouts).
     * HTTP 502 Bad Gateway
     */
    @ExceptionHandler(KrogerApiException.class)
    public ResponseEntity<ErrorBody> krogerApi(KrogerApiException ex) {
        try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.API_ERROR)) {
            log.error("Kroger API error: {}", ex.getMessage(), ex);
        }
        return ResponseEntity.status(502)
                .body(new ErrorBody("API_ERROR", ex.getMessage()));
    }

    /**
     * Handles Spring Data / JDBC failures.
     * HTTP 500
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorBody> db(DataAccessException ex) {
        try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.DB_ERROR)) {
            log.error("Database error: {}", ex.getMessage(), ex);
        }
        return ResponseEntity.status(500)
                .body(new ErrorBody("DB_ERROR", "A database error occurred."));
    }

    /**
     * Catch-all for any unhandled exception.
     * HTTP 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> other(Exception ex) {
        try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.UNCLASSIFIED)) {
            log.error("Unhandled error: {}", ex.getMessage(), ex);
        }
        return ResponseEntity.status(500)
                .body(new ErrorBody("UNCLASSIFIED", "An unexpected error occurred."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException ex) {
        try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.VALIDATION_ERROR)) {
            log.warn("Request body missing or unreadable: {}", ex.getMessage());
        }
        return ResponseEntity.badRequest()
                .body(new ErrorBody("VALIDATION_ERROR", "Request body is required and must be valid JSON."));
    }

    @ExceptionHandler(GeoAreaNotFoundException.class)
    public ResponseEntity<ErrorBody> geoNotFound(GeoAreaNotFoundException ex) {
        try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.VALIDATION_ERROR)) {
            log.warn("Geo area not found: {}", ex.getMessage());
        }
        return ResponseEntity.status(404)
                .body(new ErrorBody("NOT_FOUND", ex.getMessage()));
    }

    /** Consistent JSON body for all error responses. */
    public record ErrorBody(String status, String message) {}
}