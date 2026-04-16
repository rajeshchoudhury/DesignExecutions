package com.microservices.principles.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Standardized error response envelope for all API errors.
 *
 * <h3>DRY Principle</h3>
 * <p>A single error format used across every endpoint. Clients can build a single
 * error-parsing strategy instead of handling different error shapes per endpoint.</p>
 *
 * <h3>DYC Principle</h3>
 * <p>The {@code code} field uses a machine-readable error code (e.g., "PRODUCT_NOT_FOUND")
 * that can be looked up in the API reference. The {@code message} field provides a
 * human-readable explanation.</p>
 */
@Schema(description = "Standard error response")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(

        @Schema(description = "ISO 8601 timestamp of the error")
        Instant timestamp,

        @Schema(description = "HTTP status code", example = "404")
        int status,

        @Schema(description = "Machine-readable error code", example = "PRODUCT_NOT_FOUND")
        String code,

        @Schema(description = "Human-readable error message")
        String message,

        @Schema(description = "Request path that caused the error", example = "/api/v1/products/123")
        String path,

        @Schema(description = "Field-level validation errors (null if not a validation error)")
        List<FieldError> fieldErrors
) {
    public record FieldError(
            @Schema(description = "Field name", example = "price")
            String field,

            @Schema(description = "Rejected value", example = "-5.00")
            Object rejectedValue,

            @Schema(description = "Validation message", example = "Price must be greater than zero")
            String message
    ) {}

    public static ApiErrorResponse of(int status, String code, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, code, message, path, null);
    }

    public static ApiErrorResponse withValidationErrors(
            int status, String code, String message, String path, List<FieldError> fieldErrors) {
        return new ApiErrorResponse(Instant.now(), status, code, message, path, fieldErrors);
    }
}
