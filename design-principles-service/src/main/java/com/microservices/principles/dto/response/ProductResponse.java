package com.microservices.principles.dto.response;

import com.microservices.principles.domain.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbound DTO representing a product in API responses.
 *
 * <h3>SOC Principle</h3>
 * <p>This record is the <em>view model</em> for the API. It flattens the embedded
 * {@code Money} value object into separate {@code price}/{@code currency} fields and
 * excludes internal fields like {@code version} that clients don't need.</p>
 *
 * <h3>YAGNI Principle</h3>
 * <p>No HATEOAS links, no embedded sub-resources, no computed fields. Just the data
 * the current UI actually renders. Links and expansions can be added when there's a
 * real consumer for them.</p>
 */
@Schema(description = "Product information returned in API responses")
public record ProductResponse(

        @Schema(description = "Unique product identifier")
        UUID id,

        @Schema(description = "Product display name", example = "Wireless Bluetooth Headphones")
        String name,

        @Schema(description = "Stock-keeping unit", example = "WBH-1000-BLK")
        String sku,

        @Schema(description = "Product description")
        String description,

        @Schema(description = "Unit price", example = "149.99")
        BigDecimal price,

        @Schema(description = "Currency code", example = "USD")
        String currency,

        @Schema(description = "Available stock count", example = "500")
        int stockQuantity,

        @Schema(description = "Product category", example = "Electronics")
        String category,

        @Schema(description = "Product lifecycle status", example = "ACTIVE")
        ProductStatus status,

        @Schema(description = "Creation timestamp")
        Instant createdAt,

        @Schema(description = "Last update timestamp")
        Instant updatedAt
) {}
