package com.microservices.principles.controller;

import com.microservices.principles.domain.exception.DuplicateSkuException;
import com.microservices.principles.domain.exception.InsufficientStockException;
import com.microservices.principles.domain.exception.InvalidProductStateException;
import com.microservices.principles.domain.exception.ProductNotFoundException;
import com.microservices.principles.dto.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Centralized exception handler translating domain exceptions into HTTP responses.
 *
 * <h3>SOC Principle</h3>
 * <p>Exception-to-HTTP mapping is done in exactly <em>one place</em>. Controllers throw
 * domain exceptions freely; they never construct error responses. This handler is the
 * single translation layer between domain language and HTTP semantics.</p>
 *
 * <h3>DRY Principle</h3>
 * <p>Without this handler, every controller method would need its own try/catch blocks
 * building {@link ApiErrorResponse} objects. Centralizing it here eliminates that
 * duplication entirely.</p>
 *
 * <h3>DYC Principle</h3>
 * <p>Each handler method documents which HTTP status it produces and what error code
 * the client should expect, creating a reference for API consumers.</p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles product-not-found scenarios.
     *
     * @return 404 Not Found with error code {@code PRODUCT_NOT_FOUND}
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleProductNotFound(
            ProductNotFoundException ex, HttpServletRequest request) {
        log.warn("Product not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(
                        HttpStatus.NOT_FOUND.value(),
                        "PRODUCT_NOT_FOUND",
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    /**
     * Handles duplicate SKU conflicts.
     *
     * @return 409 Conflict with error code {@code DUPLICATE_SKU}
     */
    @ExceptionHandler(DuplicateSkuException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateSku(
            DuplicateSkuException ex, HttpServletRequest request) {
        log.warn("Duplicate SKU: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(
                        HttpStatus.CONFLICT.value(),
                        "DUPLICATE_SKU",
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    /**
     * Handles insufficient stock errors.
     *
     * @return 409 Conflict with error code {@code INSUFFICIENT_STOCK}
     */
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiErrorResponse> handleInsufficientStock(
            InsufficientStockException ex, HttpServletRequest request) {
        log.warn("Insufficient stock: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(
                        HttpStatus.CONFLICT.value(),
                        "INSUFFICIENT_STOCK",
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    /**
     * Handles invalid product state transitions.
     *
     * @return 422 Unprocessable Entity with error code {@code INVALID_PRODUCT_STATE}
     */
    @ExceptionHandler(InvalidProductStateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidState(
            InvalidProductStateException ex, HttpServletRequest request) {
        log.warn("Invalid product state: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorResponse.of(
                        HttpStatus.UNPROCESSABLE_ENTITY.value(),
                        "INVALID_PRODUCT_STATE",
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    /**
     * Handles Bean Validation failures (e.g., @NotNull, @Size violations).
     *
     * @return 400 Bad Request with error code {@code VALIDATION_FAILED} and field-level details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ApiErrorResponse.FieldError(
                        fe.getField(),
                        fe.getRejectedValue(),
                        fe.getDefaultMessage()))
                .toList();

        log.warn("Validation failed: {} errors", fieldErrors.size());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.withValidationErrors(
                        HttpStatus.BAD_REQUEST.value(),
                        "VALIDATION_FAILED",
                        "Request validation failed with %d error(s)".formatted(fieldErrors.size()),
                        request.getRequestURI(),
                        fieldErrors));
    }

    /**
     * Catch-all handler for unexpected exceptions.
     *
     * @return 500 Internal Server Error with error code {@code INTERNAL_ERROR}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "INTERNAL_ERROR",
                        "An unexpected error occurred",
                        request.getRequestURI()));
    }
}
