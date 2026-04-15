package com.microservices.common.events.order;

import com.microservices.common.dto.OrderItemData;
import lombok.Value;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Value
public class OrderCreatedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    String orderId;
    String customerId;
    List<OrderItemData> items;
    BigDecimal totalAmount;
    Instant timestamp;
}
