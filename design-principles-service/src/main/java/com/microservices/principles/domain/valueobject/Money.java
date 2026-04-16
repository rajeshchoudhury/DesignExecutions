package com.microservices.principles.domain.valueobject;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Immutable value object representing a monetary amount with its currency.
 *
 * <h3>SOC Principle</h3>
 * <p>Monetary arithmetic is a <em>domain concern</em>, not a service or controller concern.
 * By encapsulating it here, we guarantee that rounding rules, currency matching, and
 * arithmetic are enforced uniformly — no service class needs to know about
 * {@link RoundingMode#HALF_UP} or scale management.</p>
 *
 * <h3>KISS Principle</h3>
 * <p>This class exposes only the operations the domain actually uses: {@link #add},
 * {@link #subtract}, {@link #multiply}, and {@link #isGreaterThan}. We deliberately
 * omit division, percentage calculation, and currency conversion because
 * <strong>YAGNI</strong> — those features aren't needed today.</p>
 *
 * <h3>Immutability</h3>
 * <p>Every mutating operation returns a <em>new</em> {@code Money} instance,
 * making this class inherently thread-safe without synchronization.</p>
 *
 * @see <a href="https://martinfowler.com/eaaCatalog/money.html">Money Pattern — Martin Fowler</a>
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Money {

    private static final int CURRENCY_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currencyCode;

    private Money(BigDecimal amount, String currencyCode) {
        this.amount = amount.setScale(CURRENCY_SCALE, ROUNDING);
        this.currencyCode = currencyCode;
    }

    /**
     * Factory method to create a Money instance.
     *
     * @param amount       the monetary amount; must not be null
     * @param currencyCode ISO 4217 currency code (e.g., "USD", "EUR")
     * @return a new Money instance with the amount scaled to 2 decimal places
     * @throws IllegalArgumentException if the currency code is not recognized
     * @throws NullPointerException     if either argument is null
     */
    public static Money of(BigDecimal amount, String currencyCode) {
        Currency.getInstance(currencyCode); // validates ISO 4217
        return new Money(amount, currencyCode);
    }

    /** Convenience factory for USD amounts. */
    public static Money usd(BigDecimal amount) {
        return of(amount, "USD");
    }

    /** Convenience factory from a double (testing/prototyping only). */
    public static Money usd(double amount) {
        return usd(BigDecimal.valueOf(amount));
    }

    /**
     * Adds another Money to this one.
     *
     * @param other the addend
     * @return a new Money representing the sum
     * @throws IllegalArgumentException if currencies don't match
     */
    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currencyCode);
    }

    /**
     * Subtracts another Money from this one.
     *
     * @param other the subtrahend
     * @return a new Money representing the difference
     * @throws IllegalArgumentException if currencies don't match
     */
    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currencyCode);
    }

    /**
     * Multiplies this Money by a scalar quantity (e.g., item count).
     *
     * @param multiplier the scalar multiplier
     * @return a new Money representing the product
     */
    public Money multiply(int multiplier) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(multiplier)), this.currencyCode);
    }

    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isPositive() {
        return this.amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    private void assertSameCurrency(Money other) {
        if (!this.currencyCode.equals(other.currencyCode)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: cannot operate on %s and %s"
                            .formatted(this.currencyCode, other.currencyCode));
        }
    }

    @Override
    public String toString() {
        return "%s %s".formatted(currencyCode, amount.toPlainString());
    }
}
