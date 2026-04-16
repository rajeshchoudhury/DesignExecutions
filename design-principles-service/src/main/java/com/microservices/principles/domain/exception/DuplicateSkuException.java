package com.microservices.principles.domain.exception;

/**
 * Thrown when attempting to create a product with a SKU that already exists.
 *
 * <h3>DYC Principle</h3>
 * <p>The exception message includes the duplicate SKU value, making log entries
 * and error responses immediately actionable without additional context lookups.</p>
 */
public class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException(String sku) {
        super("A product with SKU '%s' already exists".formatted(sku));
    }
}
