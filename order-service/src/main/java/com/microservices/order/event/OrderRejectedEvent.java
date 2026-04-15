package com.microservices.order.event;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Published when a saga step fails and the order cannot be fulfilled.
 * Carries the rejection reason for downstream consumers and audit trails.
 */
@Value
@Builder
public class OrderRejectedEvent {

    String orderId;
    String reason;
    Instant rejectedAt;
}
