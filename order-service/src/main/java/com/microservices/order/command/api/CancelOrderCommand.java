package com.microservices.order.command.api;

import lombok.Builder;
import lombok.Value;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Cancels an order, optionally as part of saga compensation.
 * The compensationReason records why the cancellation occurred (e.g., payment timeout,
 * inventory unavailable, or explicit customer request).
 */
@Value
@Builder
public class CancelOrderCommand {

    @TargetAggregateIdentifier
    String orderId;
    String compensationReason;
}
