package com.microservices.principles.domain.exception;

/**
 * Thrown when a domain operation is attempted on a product in an invalid lifecycle state.
 *
 * @see com.microservices.principles.domain.entity.ProductStatus
 */
public class InvalidProductStateException extends RuntimeException {

    public InvalidProductStateException(String message) {
        super(message);
    }
}
