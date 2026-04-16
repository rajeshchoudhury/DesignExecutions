package com.microservices.principles.domain.exception;

/**
 * Thrown when a stock reservation request exceeds the available inventory.
 *
 * @see com.microservices.principles.domain.entity.Product#reserveStock(int)
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
