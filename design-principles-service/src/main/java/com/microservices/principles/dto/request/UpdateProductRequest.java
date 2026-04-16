package com.microservices.principles.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Inbound DTO for updating an existing product.
 *
 * <h3>YAGNI Principle</h3>
 * <p>Only updatable fields are exposed. The SKU (business key) and status
 * (controlled via dedicated endpoints) are intentionally excluded.</p>
 */
@Schema(description = "Request payload to update product attributes")
public record UpdateProductRequest(

        @NotBlank(message = "Product name is required")
        @Size(min = 3, max = 255)
        @Schema(description = "Product display name", example = "Wireless Bluetooth Headphones Pro")
        String name,

        @Size(max = 2000)
        @Schema(description = "Updated product description")
        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01")
        @Digits(integer = 17, fraction = 2)
        @Schema(description = "Updated unit price", example = "179.99")
        BigDecimal price,

        @Size(min = 3, max = 3)
        @Schema(description = "ISO 4217 currency code", example = "USD")
        String currencyCode,

        @NotBlank(message = "Category is required")
        @Size(max = 100)
        @Schema(description = "Updated product category", example = "Electronics")
        String category
) {
    public UpdateProductRequest {
        if (currencyCode == null || currencyCode.isBlank()) {
            currencyCode = "USD";
        }
    }
}
