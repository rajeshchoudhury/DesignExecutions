package com.microservices.principles.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Inbound DTO for creating a new product.
 *
 * <h3>SOC Principle</h3>
 * <p>This record is the <em>API contract</em> — it defines what the client sends.
 * It is decoupled from the {@code Product} entity so that internal domain changes
 * (new fields, renamed columns) don't break the public API, and vice versa.</p>
 *
 * <h3>DYC Principle</h3>
 * <p>Every field carries both Bean Validation constraints (enforced at runtime) and
 * OpenAPI {@link Schema} annotations (rendered in Swagger UI). The constraint messages
 * serve as executable documentation.</p>
 *
 * @param name          display name (3–255 chars)
 * @param sku           unique stock-keeping unit (3–50 chars, alphanumeric + dashes)
 * @param description   optional long description (up to 2000 chars)
 * @param price         unit price in the specified currency; must be positive
 * @param currencyCode  ISO 4217 currency code (default: USD)
 * @param stockQuantity initial stock count; must be >= 0
 * @param category      product category for filtering and reporting
 */
@Schema(description = "Request payload to create a new product in DRAFT status")
public record CreateProductRequest(

        @NotBlank(message = "Product name is required")
        @Size(min = 3, max = 255, message = "Name must be between 3 and 255 characters")
        @Schema(description = "Product display name", example = "Wireless Bluetooth Headphones")
        String name,

        @NotBlank(message = "SKU is required")
        @Size(min = 3, max = 50, message = "SKU must be between 3 and 50 characters")
        @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "SKU must be alphanumeric with dashes only")
        @Schema(description = "Unique stock-keeping unit", example = "WBH-1000-BLK")
        String sku,

        @Size(max = 2000, message = "Description cannot exceed 2000 characters")
        @Schema(description = "Detailed product description", example = "Premium noise-cancelling headphones with 30-hour battery life")
        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "Price must have at most 2 decimal places")
        @Schema(description = "Unit price", example = "149.99")
        BigDecimal price,

        @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
        @Schema(description = "ISO 4217 currency code", example = "USD", defaultValue = "USD")
        String currencyCode,

        @NotNull(message = "Stock quantity is required")
        @Min(value = 0, message = "Stock quantity cannot be negative")
        @Schema(description = "Initial stock count", example = "500")
        Integer stockQuantity,

        @NotBlank(message = "Category is required")
        @Size(max = 100, message = "Category cannot exceed 100 characters")
        @Schema(description = "Product category", example = "Electronics")
        String category
) {
    /**
     * Compact constructor providing default currency when not specified.
     */
    public CreateProductRequest {
        if (currencyCode == null || currencyCode.isBlank()) {
            currencyCode = "USD";
        }
    }
}
