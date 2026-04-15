package com.microservices.order.saga;

/**
 * Tracks the current phase of the OrderSaga orchestration.
 * Used within the saga to determine which compensating actions are needed
 * if a step fails partway through the distributed transaction.
 */
public enum SagaState {

    STARTED,
    INVENTORY_RESERVING,
    INVENTORY_RESERVED,
    PAYMENT_PROCESSING,
    PAYMENT_PROCESSED,
    COMPLETING,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}
