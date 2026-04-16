package com.microservices.principles.service.strategy;

import com.microservices.principles.domain.valueobject.Money;

/**
 * Strategy interface for product pricing calculations.
 *
 * <h3>KISS Principle — Strategy Pattern</h3>
 * <p>Instead of a monolithic {@code if/else} chain in the service layer checking
 * category-specific pricing rules, each pricing strategy is a separate, focused class.
 * New pricing rules are added by implementing this interface and registering the bean —
 * <em>no existing code is modified</em> (Open/Closed Principle as a natural consequence
 * of KISS).</p>
 *
 * <h3>YAGNI Consideration</h3>
 * <p>We start with a single {@link StandardPricingStrategy}. A {@code PromotionalPricingStrategy}
 * or {@code TieredPricingStrategy} can be added later by simply providing another
 * implementation — the service layer is already wired to accept any {@code PricingStrategy}.</p>
 *
 * @see StandardPricingStrategy
 */
public interface PricingStrategy {

    /**
     * Calculates the final price for a product.
     *
     * @param basePrice the base price set by the product manager
     * @param category  the product category (may influence pricing rules)
     * @return the calculated price after applying strategy-specific rules
     */
    Money calculatePrice(Money basePrice, String category);
}
