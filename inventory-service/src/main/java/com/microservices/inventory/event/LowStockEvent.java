package com.microservices.inventory.event;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LowStockEvent {
    String productId;
    String sku;
    int currentQuantity;
    int reorderThreshold;
}
