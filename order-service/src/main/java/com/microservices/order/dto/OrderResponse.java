package com.microservices.order.dto;

import com.microservices.order.domain.OrderItem;
import com.microservices.order.domain.OrderStatus;
import com.microservices.order.query.projection.OrderItemsConverter;
import com.microservices.order.query.projection.OrderView;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Outbound REST response representing the current state of an order.
 * Maps from the CQRS read model {@link OrderView}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private String orderId;
    private String customerId;
    private List<OrderItem> items;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private String rejectionReason;

    public static OrderResponse fromView(OrderView view) {
        return OrderResponse.builder()
                .orderId(view.getOrderId())
                .customerId(view.getCustomerId())
                .items(OrderItemsConverter.fromJson(view.getItems()))
                .totalAmount(view.getTotalAmount())
                .status(view.getStatus())
                .createdAt(view.getCreatedAt())
                .updatedAt(view.getUpdatedAt())
                .rejectionReason(view.getRejectionReason())
                .build();
    }
}
