package com.microservices.order.command.api;

import lombok.Builder;
import lombok.Value;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Rejects an order due to a failed saga step (e.g., insufficient inventory or payment failure).
 * Carries the rejection reason for audit and customer notification purposes.
 */
@Value
@Builder
public class RejectOrderCommand {

    @TargetAggregateIdentifier
    String orderId;
    String reason;
}
