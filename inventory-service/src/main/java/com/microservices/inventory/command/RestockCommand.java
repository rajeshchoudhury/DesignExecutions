package com.microservices.inventory.command;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RestockCommand {
    String productId;
    int quantity;
    String reason;
}
