package com.microservices.inventory.command;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReleaseInventoryCommand {
    String reservationId;
    String orderId;
    String reason;
}
