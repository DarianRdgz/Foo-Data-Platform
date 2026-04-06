package com.fooholdings.fdp.api.web;

import java.time.Instant;

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

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.VALIDATION_ERROR)) {
            log.warn("Request validation failed: {}", ex.getMessage());
        }
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");

        return ResponseEntity.badRequest()
                .body(new ErrorBody(
                        "VALIDATION_ERROR",
                        detail,
                        request.getRequestURI(),
                        Instant.now()
                ));
    }

    @ExceptionHandler(IngestionLockException.class)
    public ResponseEntity<ErrorBody> lock(IngestionLockException ex, HttpServletRequest request) {
        try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.LOCK_ERROR)) {
            log.warn("Ingestion lock contention: {}", ex.getMessage());
        }
        return ResponseEntity.status(409)
                .body(new ErrorBody(
                        "LOCKED",
                        ex.getMessage(),
                        request.getRequestURI(),
                        Instant.now()
                ));
    }

    @ExceptionHandler(KrogerApiException.class)
    public ResponseEntity<ErrorBody> krogerApi(KrogerApiException ex, HttpServletRequest request) {
        try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.API_ERROR)) {
            log.error("Kroger API error: {}", ex.getMessage(), ex);
        }
        return ResponseEntity.status(502)
                .body(new ErrorBody(
                        "API_ERROR",
                        ex.getMessage(),
                        request.getRequestURI(),
                        Instant.now()
                ));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorBody> db(DataAccessException ex, HttpServletRequest request) {
        try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.DB_ERROR)) {
            log.error("Database error: {}", ex.getMessage(), ex);
        }
        return ResponseEntity.status(500)
                .body(new ErrorBody(
                        "DB_ERROR",
                        "A database error occurred.",
                        request.getRequestURI(),
                        Instant.now()
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> illegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.VALIDATION_ERROR)) {
            log.warn("Bad request: {}", ex.getMessage());
        }
        return ResponseEntity.badRequest()
                .body(new ErrorBody(
                        "VALIDATION_ERROR",
                        ex.getMessage(),
                        request.getRequestURI(),
                        Instant.now()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> other(Exception ex, HttpServletRequest request) {
        try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.UNCLASSIFIED)) {
            log.error("Unhandled error: {}", ex.getMessage(), ex);
        }
        return ResponseEntity.status(500)
                .body(new ErrorBody(
                        "UNCLASSIFIED",
                        "An unexpected error occurred.",
                        request.getRequestURI(),
                        Instant.now()
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.VALIDATION_ERROR)) {
            log.warn("Request body missing or unreadable: {}", ex.getMessage());
        }
        return ResponseEntity.badRequest()
                .body(new ErrorBody(
                        "VALIDATION_ERROR",
                        "Request body is required and must be valid JSON.",
                        request.getRequestURI(),
                        Instant.now()
                ));
    }

    @ExceptionHandler(GeoAreaNotFoundException.class)
    public ResponseEntity<ErrorBody> geoNotFound(GeoAreaNotFoundException ex, HttpServletRequest request) {
        try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.VALIDATION_ERROR)) {
            log.warn("Geo area not found: {}", ex.getMessage());
        }
        return ResponseEntity.status(404)
                .body(new ErrorBody(
                        "NOT_FOUND",
                        ex.getMessage(),
                        request.getRequestURI(),
                        Instant.now()
                ));
    }

    public record ErrorBody(
            String status,
            String message,
            String path,
            Instant timestamp
    ) {}
}