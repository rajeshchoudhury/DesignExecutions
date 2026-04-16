package com.microservices.principles.domain.event;

import com.microservices.principles.domain.entity.Product;
import com.microservices.principles.domain.valueobject.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * Sealed hierarchy of domain events raised by {@link Product} aggregate operations.
 *
 * <h3>SOC Principle</h3>
 * <p>Domain events capture <em>what happened</em> in the domain. They carry no knowledge
 * of how the event will be consumed (email? Kafka? audit log?). That's the concern of
 * the infrastructure/service layer.</p>
 *
 * <h3>KISS Principle</h3>
 * <p>Using Java 21 sealed interfaces with records produces immutable, pattern-matchable
 * event types with minimal boilerplate.</p>
 *
 * @see Product
 */
public sealed interface ProductEvent {

    UUID productId();
    Instant occurredAt();

    record Created(UUID productId, String sku, String name, Instant occurredAt) implements ProductEvent {}
    record Activated(UUID productId, String sku, Instant occurredAt) implements ProductEvent {}
    record Discontinued(UUID productId, String sku, Instant occurredAt) implements ProductEvent {}
    record StockReserved(UUID productId, String sku, int quantity, Instant occurredAt) implements ProductEvent {}
    record StockReleased(UUID productId, String sku, int quantity, Instant occurredAt) implements ProductEvent {}
    record PriceChanged(UUID productId, String sku, Money oldPrice, Money newPrice, Instant occurredAt) implements ProductEvent {}

    static Created created(Product p) {
        return new Created(p.getId(), p.getSku(), p.getName(), Instant.now());
    }

    static Activated activated(Product p) {
        return new Activated(p.getId(), p.getSku(), Instant.now());
    }

    static Discontinued discontinued(Product p) {
        return new Discontinued(p.getId(), p.getSku(), Instant.now());
    }

    static StockReserved stockReserved(Product p, int qty) {
        return new StockReserved(p.getId(), p.getSku(), qty, Instant.now());
    }

    static StockReleased stockReleased(Product p, int qty) {
        return new StockReleased(p.getId(), p.getSku(), qty, Instant.now());
    }

    static PriceChanged priceChanged(Product p, Money oldPrice, Money newPrice) {
        return new PriceChanged(p.getId(), p.getSku(), oldPrice, newPrice, Instant.now());
    }
}
