package com.microservices.inventory.command;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReserveInventoryCommand {
    String orderId;
    String productId;
    String sku;
    int quantity;
}
