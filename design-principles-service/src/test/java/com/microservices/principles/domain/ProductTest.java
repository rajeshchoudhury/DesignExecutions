package com.microservices.principles.domain;

import com.microservices.principles.domain.entity.Product;
import com.microservices.principles.domain.entity.ProductStatus;
import com.microservices.principles.domain.event.ProductEvent;
import com.microservices.principles.domain.exception.InsufficientStockException;
import com.microservices.principles.domain.exception.InvalidProductStateException;
import com.microservices.principles.domain.valueobject.Money;
import com.microservices.principles.fixture.ProductFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link Product} aggregate root.
 *
 * <h3>TDD Principle</h3>
 * <p>These tests were written to define the Product's behavior <em>before</em> the
 * implementation. Each test class maps to a behavioral concern:</p>
 * <ul>
 *   <li>{@link Creation} — factory method behavior and invariants</li>
 *   <li>{@link Lifecycle} — state transitions (DRAFT → ACTIVE → DISCONTINUED)</li>
 *   <li>{@link StockManagement} — reservation and release logic</li>
 *   <li>{@link PriceManagement} — price update rules</li>
 *   <li>{@link DomainEvents} — event collection and clearing</li>
 * </ul>
 *
 * <h3>KISS Principle — Fixture Usage</h3>
 * <p>Tests use {@link ProductFixture} to keep setup concise and focused on the
 * scenario being tested.</p>
 */
@DisplayName("Product Aggregate Root")
class ProductTest {

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create product in DRAFT status with valid inputs")
        void shouldCreateInDraftStatus() {
            Product product = ProductFixture.aProduct().build();

            assertThat(product.getStatus()).isEqualTo(ProductStatus.DRAFT);
            assertThat(product.getName()).isEqualTo(ProductFixture.DEFAULT_NAME);
            assertThat(product.getSku()).isEqualTo(ProductFixture.DEFAULT_SKU);
            assertThat(product.getStockQuantity()).isEqualTo(ProductFixture.DEFAULT_STOCK);
        }

        @Test
        @DisplayName("should emit Created event on creation")
        void shouldEmitCreatedEvent() {
            Product product = ProductFixture.aProduct().build();

            assertThat(product.getDomainEvents())
                    .hasSize(1)
                    .first()
                    .isInstanceOf(ProductEvent.Created.class);
        }

        @Test
        @DisplayName("should reject non-positive price")
        void shouldRejectNonPositivePrice() {
            assertThatThrownBy(() -> ProductFixture.aProduct().withPrice(0.00).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("should reject negative stock quantity")
        void shouldRejectNegativeStock() {
            assertThatThrownBy(() -> ProductFixture.aProduct().withStockQuantity(-1).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negative");
        }
    }

    @Nested
    @DisplayName("Lifecycle Transitions")
    class Lifecycle {

        @Test
        @DisplayName("should activate a DRAFT product")
        void shouldActivate_whenDraft() {
            Product product = ProductFixture.aProduct().build();
            product.clearDomainEvents();

            product.activate();

            assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
            assertThat(product.getDomainEvents())
                    .hasSize(1)
                    .first()
                    .isInstanceOf(ProductEvent.Activated.class);
        }

        @Test
        @DisplayName("should not activate an already active product")
        void shouldRejectActivation_whenAlreadyActive() {
            Product product = ProductFixture.aProduct().build();
            product.activate();

            assertThatThrownBy(product::activate)
                    .isInstanceOf(InvalidProductStateException.class)
                    .hasMessageContaining("activate");
        }

        @Test
        @DisplayName("should discontinue an active product")
        void shouldDiscontinue_whenActive() {
            Product product = ProductFixture.aProduct().build();
            product.activate();
            product.clearDomainEvents();

            product.discontinue();

            assertThat(product.getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
            assertThat(product.getDomainEvents())
                    .hasSize(1)
                    .first()
                    .isInstanceOf(ProductEvent.Discontinued.class);
        }

        @Test
        @DisplayName("should discontinue a draft product")
        void shouldDiscontinue_whenDraft() {
            Product product = ProductFixture.aProduct().build();
            product.clearDomainEvents();

            product.discontinue();

            assertThat(product.getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
        }

        @Test
        @DisplayName("should reject discontinuing an already discontinued product")
        void shouldRejectDiscontinue_whenAlreadyDiscontinued() {
            Product product = ProductFixture.aProduct().build();
            product.discontinue();

            assertThatThrownBy(product::discontinue)
                    .isInstanceOf(InvalidProductStateException.class)
                    .hasMessageContaining("discontinued");
        }
    }

    @Nested
    @DisplayName("Stock Management")
    class StockManagement {

        @Test
        @DisplayName("should reserve stock when sufficient quantity available")
        void shouldReserveStock_whenSufficient() {
            Product product = ProductFixture.aProduct().withStockQuantity(50).build();
            product.clearDomainEvents();

            product.reserveStock(30);

            assertThat(product.getStockQuantity()).isEqualTo(20);
            assertThat(product.getDomainEvents())
                    .hasSize(1)
                    .first()
                    .isInstanceOf(ProductEvent.StockReserved.class);
        }

        @Test
        @DisplayName("should throw when reserving more than available stock")
        void shouldThrow_whenInsufficientStock() {
            Product product = ProductFixture.aProduct().withStockQuantity(5).build();

            assertThatThrownBy(() -> product.reserveStock(10))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("5 units available but 10 were requested");
        }

        @Test
        @DisplayName("should reject zero or negative reserve quantity")
        void shouldRejectInvalidReserveQuantity() {
            Product product = ProductFixture.aProduct().build();

            assertThatThrownBy(() -> product.reserveStock(0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> product.reserveStock(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should release stock correctly")
        void shouldReleaseStock() {
            Product product = ProductFixture.aProduct().withStockQuantity(50).build();
            product.reserveStock(20);
            product.clearDomainEvents();

            product.releaseStock(10);

            assertThat(product.getStockQuantity()).isEqualTo(40);
            assertThat(product.getDomainEvents())
                    .hasSize(1)
                    .first()
                    .isInstanceOf(ProductEvent.StockReleased.class);
        }

        @Test
        @DisplayName("should report stock availability correctly")
        void shouldReportStockAvailability() {
            Product product = ProductFixture.aProduct().withStockQuantity(10).build();

            assertThat(product.hasStock(10)).isTrue();
            assertThat(product.hasStock(11)).isFalse();
        }
    }

    @Nested
    @DisplayName("Price Management")
    class PriceManagement {

        @Test
        @DisplayName("should update price for active product")
        void shouldUpdatePrice_whenActive() {
            Product product = ProductFixture.aProduct().build();
            product.activate();
            product.clearDomainEvents();

            Money newPrice = Money.usd(199.99);
            product.updatePrice(newPrice);

            assertThat(product.getPrice()).isEqualTo(newPrice);
            assertThat(product.getDomainEvents())
                    .hasSize(1)
                    .first()
                    .isInstanceOf(ProductEvent.PriceChanged.class);
        }

        @Test
        @DisplayName("should reject price update for discontinued product")
        void shouldRejectPriceUpdate_whenDiscontinued() {
            Product product = ProductFixture.aProduct().build();
            product.discontinue();

            assertThatThrownBy(() -> product.updatePrice(Money.usd(50.00)))
                    .isInstanceOf(InvalidProductStateException.class)
                    .hasMessageContaining("discontinued");
        }

        @Test
        @DisplayName("should reject non-positive price update")
        void shouldRejectNonPositivePrice() {
            Product product = ProductFixture.aProduct().build();

            assertThatThrownBy(() -> product.updatePrice(Money.usd(0.00)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Domain Events")
    class DomainEvents {

        @Test
        @DisplayName("should collect multiple events")
        void shouldCollectMultipleEvents() {
            Product product = ProductFixture.aProduct().build();
            product.activate();
            product.reserveStock(5);

            assertThat(product.getDomainEvents()).hasSize(3);
        }

        @Test
        @DisplayName("should clear events after publishing")
        void shouldClearEvents() {
            Product product = ProductFixture.aProduct().build();
            assertThat(product.getDomainEvents()).isNotEmpty();

            product.clearDomainEvents();

            assertThat(product.getDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("should return unmodifiable event list")
        void shouldReturnUnmodifiableEventList() {
            Product product = ProductFixture.aProduct().build();

            assertThatThrownBy(() -> product.getDomainEvents().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
