package com.microservices.order.command.api;

import lombok.Builder;
import lombok.Value;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Marks an approved order as completed — typically after fulfillment or delivery confirmation.
 */
@Value
@Builder
public class CompleteOrderCommand {

    @TargetAggregateIdentifier
    String orderId;
}
