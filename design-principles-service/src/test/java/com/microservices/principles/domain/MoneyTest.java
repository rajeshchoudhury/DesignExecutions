package com.microservices.principles.domain;

import com.microservices.principles.domain.valueobject.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link Money} value object.
 *
 * <h3>TDD Principle — Test Structure</h3>
 * <p>Tests are organized using nested classes that mirror the behavior being tested.
 * Each test follows the Arrange-Act-Assert pattern with BDD-style naming:
 * {@code should[ExpectedBehavior]_when[Condition]}.</p>
 *
 * <h3>KISS Principle — No Spring Context</h3>
 * <p>These tests require zero infrastructure. They run in milliseconds because
 * they test pure domain logic — exactly as it should be when SOC is done right.</p>
 */
@DisplayName("Money Value Object")
class MoneyTest {

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create money with correct amount and currency")
        void shouldCreateMoney_whenValidInputs() {
            Money money = Money.of(new BigDecimal("99.99"), "USD");

            assertThat(money.getAmount()).isEqualByComparingTo(new BigDecimal("99.99"));
            assertThat(money.getCurrencyCode()).isEqualTo("USD");
        }

        @Test
        @DisplayName("should scale amount to 2 decimal places")
        void shouldScaleAmount_whenMoreDecimalPlaces() {
            Money money = Money.of(new BigDecimal("99.999"), "USD");

            assertThat(money.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("should reject invalid currency code")
        void shouldThrow_whenInvalidCurrency() {
            assertThatThrownBy(() -> Money.of(BigDecimal.TEN, "INVALID"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should create USD money via convenience factory")
        void shouldCreateUsd_viaConvenienceFactory() {
            Money money = Money.usd(42.50);

            assertThat(money.getCurrencyCode()).isEqualTo("USD");
            assertThat(money.getAmount()).isEqualByComparingTo(new BigDecimal("42.50"));
        }
    }

    @Nested
    @DisplayName("Arithmetic")
    class Arithmetic {

        @Test
        @DisplayName("should add two money amounts of same currency")
        void shouldAdd_whenSameCurrency() {
            Money a = Money.usd(10.00);
            Money b = Money.usd(20.50);

            Money result = a.add(b);

            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("30.50"));
            assertThat(result.getCurrencyCode()).isEqualTo("USD");
        }

        @Test
        @DisplayName("should subtract money amounts")
        void shouldSubtract_whenSameCurrency() {
            Money a = Money.usd(50.00);
            Money b = Money.usd(20.00);

            Money result = a.subtract(b);

            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
        }

        @Test
        @DisplayName("should multiply by integer quantity")
        void shouldMultiply_byQuantity() {
            Money price = Money.usd(25.00);

            Money total = price.multiply(3);

            assertThat(total.getAmount()).isEqualByComparingTo(new BigDecimal("75.00"));
        }

        @Test
        @DisplayName("should reject arithmetic across different currencies")
        void shouldThrow_whenDifferentCurrencies() {
            Money usd = Money.usd(10.00);
            Money eur = Money.of(BigDecimal.TEN, "EUR");

            assertThatThrownBy(() -> usd.add(eur))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency mismatch");
        }
    }

    @Nested
    @DisplayName("Comparisons")
    class Comparisons {

        @Test
        @DisplayName("should detect positive amount")
        void shouldBePositive_whenAmountGreaterThanZero() {
            assertThat(Money.usd(1.00).isPositive()).isTrue();
            assertThat(Money.usd(0.00).isPositive()).isFalse();
        }

        @Test
        @DisplayName("should detect zero amount")
        void shouldBeZero_whenAmountIsZero() {
            assertThat(Money.usd(0.00).isZero()).isTrue();
            assertThat(Money.usd(0.01).isZero()).isFalse();
        }

        @Test
        @DisplayName("should compare money amounts")
        void shouldCompare_greaterThan() {
            Money big = Money.usd(100.00);
            Money small = Money.usd(50.00);

            assertThat(big.isGreaterThan(small)).isTrue();
            assertThat(small.isGreaterThan(big)).isFalse();
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("should return new instance on arithmetic operations")
        void shouldReturnNewInstance_onArithmetic() {
            Money original = Money.usd(100.00);
            Money result = original.add(Money.usd(50.00));

            assertThat(result).isNotSameAs(original);
            assertThat(original.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        }
    }
}
