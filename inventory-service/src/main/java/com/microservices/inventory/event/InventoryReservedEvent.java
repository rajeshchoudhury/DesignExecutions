package com.microservices.inventory.event;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class InventoryReservedEvent {
    String reservationId;
    String orderId;
    String productId;
    int quantity;
    Instant reservedAt;
}
