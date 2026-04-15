package com.microservices.order.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents the lifecycle states of an order and enforces valid state transitions.
 * The transition rules prevent illegal operations — for example, a REJECTED order
 * cannot be approved, and only APPROVED orders can be completed.
 */
public enum OrderStatus {

    CREATED,
    APPROVED,
    REJECTED,
    PAYMENT_PENDING,
    PAYMENT_PROCESSED,
    INVENTORY_RESERVED,
    COMPLETED,
    CANCELLED,
    COMPENSATING;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            CREATED, EnumSet.of(PAYMENT_PENDING, INVENTORY_RESERVED, APPROVED, REJECTED, CANCELLED, COMPENSATING),
            PAYMENT_PENDING, EnumSet.of(PAYMENT_PROCESSED, REJECTED, CANCELLED, COMPENSATING),
            PAYMENT_PROCESSED, EnumSet.of(APPROVED, REJECTED, CANCELLED, COMPENSATING),
            INVENTORY_RESERVED, EnumSet.of(PAYMENT_PENDING, APPROVED, REJECTED, CANCELLED, COMPENSATING),
            APPROVED, EnumSet.of(COMPLETED, CANCELLED),
            REJECTED, EnumSet.noneOf(OrderStatus.class),
            COMPLETED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class),
            COMPENSATING, EnumSet.of(CANCELLED, REJECTED)
    );

    public boolean canTransitionTo(OrderStatus target) {
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    public void validateTransition(OrderStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Invalid order status transition from %s to %s".formatted(this, target));
        }
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED;
    }
}
