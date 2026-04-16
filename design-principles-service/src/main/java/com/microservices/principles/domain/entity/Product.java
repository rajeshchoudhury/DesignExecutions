package com.microservices.principles.domain.entity;

import com.microservices.principles.domain.event.ProductEvent;
import com.microservices.principles.domain.exception.InsufficientStockException;
import com.microservices.principles.domain.exception.InvalidProductStateException;
import com.microservices.principles.domain.valueobject.Money;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root for the Product domain.
 *
 * <h3>SOC Principle — Domain Logic Lives Here</h3>
 * <p>Business rules such as stock reservation, activation lifecycle, and price validation
 * are <em>domain</em> responsibilities. They are not in the service layer, not in the
 * controller, and not in the repository. This makes the domain testable in complete
 * isolation (no Spring context needed).</p>
 *
 * <h3>DRY Principle — Inherits from {@link BaseEntity}</h3>
 * <p>Identity, versioning, and audit timestamps come from the base class.</p>
 *
 * <h3>KISS Principle — Explicit State Machine</h3>
 * <p>Product lifecycle follows a simple state machine: {@code DRAFT → ACTIVE → DISCONTINUED}.
 * Each transition has a single guard method with a clear precondition. No generic state-machine
 * framework is used because <strong>YAGNI</strong>.</p>
 *
 * <h3>Domain Events</h3>
 * <p>Events are collected internally and published by the service layer after persistence,
 * following the "collect-then-dispatch" pattern to avoid publishing events from aborted
 * transactions.</p>
 *
 * @see BaseEntity
 * @see ProductStatus
 */
@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_product_sku", columnList = "sku", unique = true),
        @Index(name = "idx_product_category", columnList = "category"),
        @Index(name = "idx_product_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = "domainEvents")
public class Product extends BaseEntity<UUID> {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "sku", nullable = false, unique = true, length = 50)
    private String sku;

    @Column(name = "description", length = 2000)
    private String description;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "price_amount")),
            @AttributeOverride(name = "currencyCode", column = @Column(name = "price_currency"))
    })
    private Money price;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status;

    @Transient
    private final List<ProductEvent> domainEvents = new ArrayList<>();

    /**
     * Private constructor enforcing creation through the static factory.
     *
     * @see #create(String, String, String, Money, int, String)
     */
    @Builder(access = AccessLevel.PRIVATE)
    private Product(String name, String sku, String description,
                    Money price, int stockQuantity, String category) {
        this.name = name;
        this.sku = sku;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.category = category;
        this.status = ProductStatus.DRAFT;
    }

    /**
     * Factory method that encapsulates creation invariants.
     *
     * <p>All new products start in {@link ProductStatus#DRAFT} status.
     * This is the <em>only</em> way to create a Product instance in application code.</p>
     *
     * @param name          product display name; must not be blank
     * @param sku           stock-keeping unit; must be unique system-wide
     * @param description   optional long description
     * @param price         must be a positive monetary amount
     * @param stockQuantity initial stock; must be >= 0
     * @param category      product category for filtering
     * @return a new Product in DRAFT status
     * @throws IllegalArgumentException if price is not positive or stock is negative
     */
    public static Product create(String name, String sku, String description,
                                 Money price, int stockQuantity, String category) {
        if (!price.isPositive()) {
            throw new IllegalArgumentException("Product price must be positive");
        }
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative");
        }

        Product product = Product.builder()
                .name(name)
                .sku(sku)
                .description(description)
                .price(price)
                .stockQuantity(stockQuantity)
                .category(category)
                .build();

        product.registerEvent(ProductEvent.created(product));
        return product;
    }

    /**
     * Transitions this product from DRAFT to ACTIVE.
     *
     * @throws InvalidProductStateException if current status is not DRAFT
     */
    public void activate() {
        assertStatus(ProductStatus.DRAFT, "activate");
        this.status = ProductStatus.ACTIVE;
        registerEvent(ProductEvent.activated(this));
    }

    /**
     * Transitions this product to DISCONTINUED.
     * Allowed from both DRAFT and ACTIVE states.
     *
     * @throws InvalidProductStateException if current status is already DISCONTINUED
     */
    public void discontinue() {
        if (this.status == ProductStatus.DISCONTINUED) {
            throw new InvalidProductStateException(
                    "Product %s is already discontinued".formatted(getSku()));
        }
        this.status = ProductStatus.DISCONTINUED;
        registerEvent(ProductEvent.discontinued(this));
    }

    /**
     * Reserves stock for an order. Decrements available quantity.
     *
     * @param quantity the number of units to reserve; must be > 0
     * @throws InsufficientStockException if available stock is less than requested
     * @throws IllegalArgumentException   if quantity <= 0
     */
    public void reserveStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reserve quantity must be positive");
        }
        if (this.stockQuantity < quantity) {
            throw new InsufficientStockException(
                    "Product %s has %d units available but %d were requested"
                            .formatted(sku, stockQuantity, quantity));
        }
        this.stockQuantity -= quantity;
        registerEvent(ProductEvent.stockReserved(this, quantity));
    }

    /**
     * Releases previously reserved stock (e.g., order cancellation).
     *
     * @param quantity the number of units to release; must be > 0
     */
    public void releaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Release quantity must be positive");
        }
        this.stockQuantity += quantity;
        registerEvent(ProductEvent.stockReleased(this, quantity));
    }

    /**
     * Updates the mutable product details. Only allowed for non-discontinued products.
     *
     * <h3>SOC Principle — Domain-Controlled Mutation</h3>
     * <p>Rather than exposing setters (which bypass invariant checks), this method
     * encapsulates the update operation with validation.</p>
     *
     * @param name        new product name
     * @param description new description
     * @param category    new category
     * @throws InvalidProductStateException if product is DISCONTINUED
     */
    public void updateDetails(String name, String description, String category) {
        if (this.status == ProductStatus.DISCONTINUED) {
            throw new InvalidProductStateException(
                    "Cannot update discontinued product %s".formatted(sku));
        }
        this.name = name;
        this.description = description;
        this.category = category;
    }

    /**
     * Updates the product price. Only allowed for DRAFT or ACTIVE products.
     *
     * @param newPrice the new price; must be positive
     * @throws InvalidProductStateException if product is DISCONTINUED
     */
    public void updatePrice(Money newPrice) {
        if (this.status == ProductStatus.DISCONTINUED) {
            throw new InvalidProductStateException(
                    "Cannot update price of discontinued product %s".formatted(sku));
        }
        if (!newPrice.isPositive()) {
            throw new IllegalArgumentException("Price must be positive");
        }
        Money oldPrice = this.price;
        this.price = newPrice;
        registerEvent(ProductEvent.priceChanged(this, oldPrice, newPrice));
    }

    /**
     * Returns an unmodifiable view of domain events collected since the last clear.
     * Used by the service layer to publish events after successful persistence.
     */
    public List<ProductEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /** Clears all pending domain events. Called after events have been published. */
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    public boolean isActive() {
        return this.status == ProductStatus.ACTIVE;
    }

    public boolean hasStock(int quantity) {
        return this.stockQuantity >= quantity;
    }

    private void assertStatus(ProductStatus expected, String operation) {
        if (this.status != expected) {
            throw new InvalidProductStateException(
                    "Cannot %s product %s: expected status %s but was %s"
                            .formatted(operation, sku, expected, status));
        }
    }

    private void registerEvent(ProductEvent event) {
        domainEvents.add(event);
    }
}
