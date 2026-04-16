package com.microservices.principles.domain.entity;

/**
 * Lifecycle states for a {@link Product}.
 *
 * <h3>KISS Principle</h3>
 * <p>Three simple states with unidirectional transitions:</p>
 * <pre>
 *   DRAFT ──→ ACTIVE ──→ DISCONTINUED
 *     │                       ▲
 *     └───────────────────────┘
 * </pre>
 *
 * <p>We deliberately avoid a generic state-machine framework (YAGNI).
 * Transition guards live in the {@link Product} entity itself.</p>
 */
public enum ProductStatus {

    /** Initial state. Product is being configured and is not yet visible to customers. */
    DRAFT,

    /** Product is live and available for purchase. */
    ACTIVE,

    /** Product has been retired. No new orders accepted; existing orders still fulfilled. */
    DISCONTINUED
}
