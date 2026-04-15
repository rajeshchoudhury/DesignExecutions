package com.microservices.order.event;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Published when the saga orchestration completes successfully —
 * inventory was reserved and payment was processed.
 */
@Value
@Builder
public class OrderApprovedEvent {

    String orderId;
    Instant approvedAt;
}
