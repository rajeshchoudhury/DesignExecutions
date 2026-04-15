package com.microservices.order.query.projection;

import com.microservices.order.domain.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read-model entity (CQRS query side).
 *
 * This table is populated by event handlers, NOT by direct writes.
 * It is optimised for fast reads and can be rebuilt at any time by replaying
 * events from the event store — a key benefit of Event Sourcing + CQRS.
 *
 * The {@code items} field stores order line items as a JSON string using
 * {@link OrderItemsConverter} so the read model remains denormalised and
 * avoids joins for list queries.
 */
@Entity
@Table(name = "order_view", indexes = {
        @Index(name = "idx_order_view_order_id", columnList = "orderId", unique = true),
        @Index(name = "idx_order_view_customer_id", columnList = "customerId"),
        @Index(name = "idx_order_view_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderId;

    @Column(nullable = false)
    private String customerId;

    @Lob
    @Column(columnDefinition = "TEXT")
    @Convert(converter = OrderItemsConverter.class)
    private String items;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    private Instant createdAt;
    private Instant updatedAt;

    @Column(length = 1024)
    private String rejectionReason;
}
