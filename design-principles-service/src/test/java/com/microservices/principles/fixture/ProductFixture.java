package com.microservices.principles.fixture;

import com.microservices.principles.domain.entity.Product;
import com.microservices.principles.domain.valueobject.Money;
import com.microservices.principles.dto.request.CreateProductRequest;
import com.microservices.principles.dto.request.UpdateProductRequest;

import java.math.BigDecimal;

/**
 * Test fixture factory for creating consistent, reusable test data.
 *
 * <h3>TDD Principle — Test Readability</h3>
 * <p>Tests should read like specifications. By extracting object creation into this
 * fixture class, test methods express <em>intent</em> rather than drowning in
 * constructor arguments:</p>
 * <pre>{@code
 * // Instead of this:
 * Product p = Product.create("Headphones", "SKU-001", "Desc",
 *     Money.of(new BigDecimal("99.99"), "USD"), 100, "Electronics");
 *
 * // Tests read like this:
 * Product p = ProductFixture.aProduct().build();
 * Product p = ProductFixture.aProduct().withPrice(49.99).build();
 * }</pre>
 *
 * <h3>DRY Principle</h3>
 * <p>Without fixtures, every test class constructs its own test data, leading to
 * fragile tests that break whenever a constructor signature changes. Fixtures
 * centralize the construction logic.</p>
 */
public final class ProductFixture {

    public static final String DEFAULT_NAME = "Wireless Bluetooth Headphones";
    public static final String DEFAULT_SKU = "WBH-1000-BLK";
    public static final String DEFAULT_DESCRIPTION = "Premium noise-cancelling headphones";
    public static final BigDecimal DEFAULT_PRICE = new BigDecimal("149.99");
    public static final String DEFAULT_CURRENCY = "USD";
    public static final int DEFAULT_STOCK = 100;
    public static final String DEFAULT_CATEGORY = "Electronics";

    private ProductFixture() {}

    /** Returns a new builder pre-filled with sensible defaults. */
    public static ProductBuilder aProduct() {
        return new ProductBuilder();
    }

    /** Returns a builder for CreateProductRequest with sensible defaults. */
    public static CreateRequestBuilder aCreateRequest() {
        return new CreateRequestBuilder();
    }

    /** Returns a builder for UpdateProductRequest with sensible defaults. */
    public static UpdateRequestBuilder anUpdateRequest() {
        return new UpdateRequestBuilder();
    }

    /**
     * Fluent builder for creating Product domain objects in tests.
     *
     * <h3>KISS Principle</h3>
     * <p>Each {@code with*()} method overrides one default value. The builder pattern
     * allows tests to customize only what's relevant to the scenario under test.</p>
     */
    public static class ProductBuilder {
        private String name = DEFAULT_NAME;
        private String sku = DEFAULT_SKU;
        private String description = DEFAULT_DESCRIPTION;
        private BigDecimal price = DEFAULT_PRICE;
        private String currency = DEFAULT_CURRENCY;
        private int stockQuantity = DEFAULT_STOCK;
        private String category = DEFAULT_CATEGORY;

        public ProductBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public ProductBuilder withSku(String sku) {
            this.sku = sku;
            return this;
        }

        public ProductBuilder withDescription(String description) {
            this.description = description;
            return this;
        }

        public ProductBuilder withPrice(double price) {
            this.price = BigDecimal.valueOf(price);
            return this;
        }

        public ProductBuilder withPrice(BigDecimal price) {
            this.price = price;
            return this;
        }

        public ProductBuilder withCurrency(String currency) {
            this.currency = currency;
            return this;
        }

        public ProductBuilder withStockQuantity(int stockQuantity) {
            this.stockQuantity = stockQuantity;
            return this;
        }

        public ProductBuilder withCategory(String category) {
            this.category = category;
            return this;
        }

        public Product build() {
            return Product.create(name, sku, description,
                    Money.of(price, currency), stockQuantity, category);
        }
    }

    public static class CreateRequestBuilder {
        private String name = DEFAULT_NAME;
        private String sku = DEFAULT_SKU;
        private String description = DEFAULT_DESCRIPTION;
        private BigDecimal price = DEFAULT_PRICE;
        private String currencyCode = DEFAULT_CURRENCY;
        private int stockQuantity = DEFAULT_STOCK;
        private String category = DEFAULT_CATEGORY;

        public CreateRequestBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public CreateRequestBuilder withSku(String sku) {
            this.sku = sku;
            return this;
        }

        public CreateRequestBuilder withPrice(double price) {
            this.price = BigDecimal.valueOf(price);
            return this;
        }

        public CreateRequestBuilder withStockQuantity(int stockQuantity) {
            this.stockQuantity = stockQuantity;
            return this;
        }

        public CreateRequestBuilder withCategory(String category) {
            this.category = category;
            return this;
        }

        public CreateProductRequest build() {
            return new CreateProductRequest(
                    name, sku, description, price, currencyCode, stockQuantity, category);
        }
    }

    public static class UpdateRequestBuilder {
        private String name = "Updated " + DEFAULT_NAME;
        private String description = "Updated " + DEFAULT_DESCRIPTION;
        private BigDecimal price = new BigDecimal("179.99");
        private String currencyCode = DEFAULT_CURRENCY;
        private String category = DEFAULT_CATEGORY;

        public UpdateRequestBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public UpdateRequestBuilder withPrice(double price) {
            this.price = BigDecimal.valueOf(price);
            return this;
        }

        public UpdateProductRequest build() {
            return new UpdateProductRequest(name, description, price, currencyCode, category);
        }
    }
}
