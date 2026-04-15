package com.microservices.order.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Value object representing a single line item within an order.
 * Immutable once created — quantity and price are captured at order time
 * to preserve historical accuracy regardless of catalog changes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem implements Serializable {

    private String productId;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
