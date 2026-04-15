package com.microservices.order.aggregate;

import com.microservices.order.command.api.*;
import com.microservices.order.domain.OrderItem;
import com.microservices.order.domain.OrderStatus;
import com.microservices.order.event.*;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Event-sourced aggregate for Order lifecycle management.
 *
 * State is never persisted directly — it is reconstructed by replaying the
 * full sequence of domain events from the event store. Each {@code @CommandHandler}
 * validates business rules against the current state, then applies an event via
 * {@link AggregateLifecycle#apply}. Each {@code @EventSourcingHandler} mutates
 * internal state so subsequent commands see a consistent view.
 */
@Aggregate(snapshotTriggerDefinition = "orderSnapshotTrigger")
@NoArgsConstructor
@Slf4j
public class OrderAggregate {

    @AggregateIdentifier
    private String orderId;
    private String customerId;
    private List<OrderItem> items;
    private BigDecimal totalAmount;
    private OrderStatus orderStatus;

    // ── Command Handlers ────────────────────────────────────────────────

    @CommandHandler
    public OrderAggregate(CreateOrderCommand command) {
        log.info("Handling CreateOrderCommand for orderId={}", command.getOrderId());

        if (command.getItems() == null || command.getItems().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }
        if (command.getTotalAmount() == null || command.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total amount must be positive");
        }

        AggregateLifecycle.apply(OrderCreatedEvent.builder()
                .orderId(command.getOrderId())
                .customerId(command.getCustomerId())
                .items(command.getItems())
                .totalAmount(command.getTotalAmount())
                .createdAt(Instant.now())
                .build());
    }

    @CommandHandler
    public void handle(ApproveOrderCommand command) {
        log.info("Handling ApproveOrderCommand for orderId={}, currentStatus={}", orderId, orderStatus);

        if (orderStatus == OrderStatus.REJECTED) {
            throw new IllegalStateException("Cannot approve an already rejected order: " + orderId);
        }
        if (orderStatus == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot approve a cancelled order: " + orderId);
        }
        if (orderStatus == OrderStatus.APPROVED || orderStatus == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Order is already approved/completed: " + orderId);
        }

        AggregateLifecycle.apply(OrderApprovedEvent.builder()
                .orderId(orderId)
                .approvedAt(Instant.now())
                .build());
    }

    @CommandHandler
    public void handle(RejectOrderCommand command) {
        log.info("Handling RejectOrderCommand for orderId={}, reason={}", orderId, command.getReason());

        if (orderStatus.isTerminal()) {
            throw new IllegalStateException(
                    "Cannot reject order in terminal state %s: %s".formatted(orderStatus, orderId));
        }

        AggregateLifecycle.apply(OrderRejectedEvent.builder()
                .orderId(orderId)
                .reason(command.getReason())
                .rejectedAt(Instant.now())
                .build());
    }

    @CommandHandler
    public void handle(CompleteOrderCommand command) {
        log.info("Handling CompleteOrderCommand for orderId={}, currentStatus={}", orderId, orderStatus);

        if (orderStatus != OrderStatus.APPROVED) {
            throw new IllegalStateException(
                    "Only approved orders can be completed. Current status: %s for order: %s"
                            .formatted(orderStatus, orderId));
        }

        AggregateLifecycle.apply(OrderCompletedEvent.builder()
                .orderId(orderId)
                .completedAt(Instant.now())
                .build());
    }

    @CommandHandler
    public void handle(CancelOrderCommand command) {
        log.info("Handling CancelOrderCommand for orderId={}, reason={}", orderId, command.getCompensationReason());

        if (orderStatus == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed order: " + orderId);
        }
        if (orderStatus == OrderStatus.CANCELLED) {
            log.warn("Order {} is already cancelled — ignoring duplicate cancel", orderId);
            return;
        }

        AggregateLifecycle.apply(OrderCancelledEvent.builder()
                .orderId(orderId)
                .reason(command.getCompensationReason())
                .cancelledAt(Instant.now())
                .build());
    }

    // ── Event Sourcing Handlers (state mutation only — no side effects) ─

    @EventSourcingHandler
    public void on(OrderCreatedEvent event) {
        this.orderId = event.getOrderId();
        this.customerId = event.getCustomerId();
        this.items = new ArrayList<>(event.getItems());
        this.totalAmount = event.getTotalAmount();
        this.orderStatus = OrderStatus.CREATED;

        log.debug("OrderAggregate state applied: CREATED for orderId={}", orderId);
    }

    @EventSourcingHandler
    public void on(OrderApprovedEvent event) {
        this.orderStatus = OrderStatus.APPROVED;
        log.debug("OrderAggregate state applied: APPROVED for orderId={}", orderId);
    }

    @EventSourcingHandler
    public void on(OrderRejectedEvent event) {
        this.orderStatus = OrderStatus.REJECTED;
        log.debug("OrderAggregate state applied: REJECTED for orderId={}", orderId);
    }

    @EventSourcingHandler
    public void on(OrderCompletedEvent event) {
        this.orderStatus = OrderStatus.COMPLETED;
        log.debug("OrderAggregate state applied: COMPLETED for orderId={}", orderId);
    }

    @EventSourcingHandler
    public void on(OrderCancelledEvent event) {
        this.orderStatus = OrderStatus.CANCELLED;
        log.debug("OrderAggregate state applied: CANCELLED for orderId={}", orderId);
    }
}
