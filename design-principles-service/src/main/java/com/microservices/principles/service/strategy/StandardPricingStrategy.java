package com.microservices.principles.service.strategy;

import com.microservices.principles.domain.valueobject.Money;
import org.springframework.stereotype.Component;

/**
 * Default pricing strategy that returns the base price unmodified.
 *
 * <h3>YAGNI Principle</h3>
 * <p>The simplest strategy that could possibly work. No tax calculation, no margin
 * adjustment, no promotional discounts. Those will be added when (and only when) a
 * business requirement demands them.</p>
 *
 * <h3>KISS Principle</h3>
 * <p>One line of logic. This is intentional — complexity is added only when justified.</p>
 */
@Component
public class StandardPricingStrategy implements PricingStrategy {

    @Override
    public Money calculatePrice(Money basePrice, String category) {
        return basePrice;
    }
}
