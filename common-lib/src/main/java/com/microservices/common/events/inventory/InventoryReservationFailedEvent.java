package com.microservices.common.events.inventory;

import lombok.Value;

import java.io.Serializable;
import java.time.Instant;

@Value
public class InventoryReservationFailedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    String orderId;
    String productId;
    String reason;
    Instant failedAt;
}
