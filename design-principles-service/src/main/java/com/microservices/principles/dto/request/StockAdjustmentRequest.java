package com.microservices.principles.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for stock reservation or release operations.
 *
 * <h3>KISS Principle</h3>
 * <p>A single, focused DTO for stock adjustments. The operation type (reserve vs. release)
 * is determined by the endpoint, not by a field in this DTO — keeping the contract
 * unambiguous.</p>
 */
@Schema(description = "Stock adjustment request for reserve/release operations")
public record StockAdjustmentRequest(

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Schema(description = "Number of units to reserve or release", example = "10")
        Integer quantity
) {}
