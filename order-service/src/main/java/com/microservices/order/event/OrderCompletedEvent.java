package com.microservices.order.event;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Published when an approved order reaches its final fulfilled state.
 */
@Value
@Builder
public class OrderCompletedEvent {

    String orderId;
    Instant completedAt;
}
