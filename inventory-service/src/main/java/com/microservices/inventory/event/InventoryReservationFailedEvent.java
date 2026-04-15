package com.microservices.inventory.event;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class InventoryReservationFailedEvent {
    String orderId;
    String productId;
    String reason;
    Instant failedAt;
}
