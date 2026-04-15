package com.microservices.order.command.api;

import lombok.Builder;
import lombok.Value;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Approves an order after all saga steps (inventory + payment) have succeeded.
 * Dispatched by the OrderSaga upon receiving confirmation from downstream services.
 */
@Value
@Builder
public class ApproveOrderCommand {

    @TargetAggregateIdentifier
    String orderId;
}
