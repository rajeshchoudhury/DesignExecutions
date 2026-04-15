package com.microservices.inventory.event;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class InventoryReleasedEvent {
    String reservationId;
    String orderId;
    Instant releasedAt;
}
