package com.microservices.order.event;

import com.microservices.order.domain.OrderItem;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Published when a new order is created. This event starts the OrderSaga
 * and populates the initial state in both the event store and the read model.
 */
@Value
@Builder
public class OrderCreatedEvent {

    String orderId;
    String customerId;
    List<OrderItem> items;
    BigDecimal totalAmount;
    Instant createdAt;
}
