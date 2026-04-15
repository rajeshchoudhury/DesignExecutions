package com.microservices.order.event;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Published when an order is cancelled — either by customer request or
 * as part of saga compensation when a downstream step fails.
 */
@Value
@Builder
public class OrderCancelledEvent {

    String orderId;
    String reason;
    Instant cancelledAt;
}
