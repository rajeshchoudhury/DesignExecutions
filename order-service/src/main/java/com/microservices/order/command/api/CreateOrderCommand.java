package com.microservices.order.command.api;

import com.microservices.order.domain.OrderItem;
import lombok.Builder;
import lombok.Value;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.math.BigDecimal;
import java.util.List;

/**
 * Initiates order creation within the Order aggregate.
 * Triggers the OrderSaga which orchestrates inventory reservation and payment processing.
 */
@Value
@Builder
public class CreateOrderCommand {

    @TargetAggregateIdentifier
    String orderId;
    String customerId;
    List<OrderItem> items;
    BigDecimal totalAmount;
}
