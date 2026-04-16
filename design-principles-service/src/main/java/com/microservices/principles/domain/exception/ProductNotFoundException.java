package com.microservices.principles.domain.exception;

import java.util.UUID;

/**
 * Thrown when a product lookup by ID or SKU yields no result.
 *
 * <h3>SOC Principle</h3>
 * <p>Domain exceptions are part of the domain language. The controller layer's
 * {@code GlobalExceptionHandler} translates them into HTTP responses — the domain
 * itself has no knowledge of HTTP status codes.</p>
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID id) {
        super("Product not found with id: " + id);
    }

    public ProductNotFoundException(String sku) {
        super("Product not found with SKU: " + sku);
    }
}
