package com.microservices.common.events.inventory;

import lombok.Value;

import java.io.Serializable;
import java.time.Instant;

@Value
public class InventoryReservedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    String reservationId;
    String orderId;
    String productId;
    int quantity;
    Instant reservedAt;
}
