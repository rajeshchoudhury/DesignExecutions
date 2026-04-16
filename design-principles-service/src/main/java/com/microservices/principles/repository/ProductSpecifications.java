package com.microservices.principles.repository;

import com.microservices.principles.domain.entity.Product;
import com.microservices.principles.domain.entity.ProductStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/**
 * Reusable JPA {@link Specification} predicates for dynamic product queries.
 *
 * <h3>DRY Principle</h3>
 * <p>Instead of writing separate repository methods for every filter combination
 * (by name, by category, by price range, by status, by name-and-category, etc.),
 * we compose atomic predicates:</p>
 * <pre>{@code
 * Specification<Product> spec = ProductSpecifications.hasCategory("Electronics")
 *     .and(ProductSpecifications.hasPriceBetween(10, 500))
 *     .and(ProductSpecifications.hasStatus(ProductStatus.ACTIVE));
 * Page<Product> results = productRepository.findAll(spec, pageable);
 * }</pre>
 *
 * <p>This eliminates the combinatorial explosion of finder methods while remaining
 * type-safe and readable.</p>
 *
 * <h3>KISS Principle</h3>
 * <p>Each specification is a single, focused predicate. Composition is done by the caller
 * using {@code .and()} / {@code .or()} — no custom query-builder DSL needed.</p>
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
        // utility class — no instantiation
    }

    /** Filters products whose name contains the given text (case-insensitive). */
    public static Specification<Product> hasNameContaining(String name) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    /** Filters products by exact category match. */
    public static Specification<Product> hasCategory(String category) {
        return (root, query, cb) ->
                cb.equal(root.get("category"), category);
    }

    /** Filters products by lifecycle status. */
    public static Specification<Product> hasStatus(ProductStatus status) {
        return (root, query, cb) ->
                cb.equal(root.get("status"), status);
    }

    /** Filters products whose price falls within the inclusive range [min, max]. */
    public static Specification<Product> hasPriceBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) ->
                cb.between(root.get("price").get("amount"), min, max);
    }

    /** Filters products with stock at or below the given threshold. */
    public static Specification<Product> hasStockBelow(int threshold) {
        return (root, query, cb) ->
                cb.lessThanOrEqualTo(root.get("stockQuantity"), threshold);
    }

    /** Filters products whose SKU starts with the given prefix. */
    public static Specification<Product> hasSkuStartingWith(String prefix) {
        return (root, query, cb) ->
                cb.like(root.get("sku"), prefix + "%");
    }
}
