package com.microservices.principles.repository;

import com.microservices.principles.domain.entity.Product;
import com.microservices.principles.domain.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Product} entities.
 *
 * <h3>SOC Principle</h3>
 * <p>This interface is the <strong>only</strong> place that knows how to talk to the
 * database for products. The service layer depends on this abstraction, never on
 * {@code EntityManager} or raw JDBC. This keeps persistence concerns out of business logic.</p>
 *
 * <h3>DRY Principle — {@link JpaSpecificationExecutor}</h3>
 * <p>Extending {@code JpaSpecificationExecutor} allows us to build dynamic queries using
 * reusable {@link com.microservices.principles.repository.ProductSpecifications} rather than
 * writing N finder methods for every filter combination.</p>
 *
 * <h3>YAGNI Principle</h3>
 * <p>We expose only the queries the application actually needs today. There is no
 * {@code findByDescriptionContaining} or {@code countByCategory} — those can be added
 * when a real user story demands them.</p>
 *
 * @see ProductSpecifications
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>,
        JpaSpecificationExecutor<Product> {

    /**
     * Finds a product by its unique stock-keeping unit.
     *
     * @param sku the SKU to look up
     * @return an Optional containing the product if found
     */
    Optional<Product> findBySku(String sku);

    /**
     * Checks whether a SKU is already in use.
     *
     * @param sku the SKU to check
     * @return true if a product with this SKU exists
     */
    boolean existsBySku(String sku);

    /**
     * Finds all products in a given status, with pagination.
     *
     * @param status   the product lifecycle status
     * @param pageable pagination and sorting parameters
     * @return a page of matching products
     */
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    /**
     * Finds all products in a given category and status.
     *
     * @param category the product category
     * @param status   the product lifecycle status
     * @param pageable pagination parameters
     * @return a page of matching products
     */
    Page<Product> findByCategoryAndStatus(String category, ProductStatus status, Pageable pageable);

    /**
     * Custom JPQL query demonstrating explicit query definition for complex needs.
     * Finds products with stock below a threshold — useful for reorder alerts.
     *
     * @param threshold the stock level threshold
     * @param pageable  pagination parameters
     * @return products with stock below the threshold
     */
    @Query("SELECT p FROM Product p WHERE p.stockQuantity < :threshold AND p.status = 'ACTIVE'")
    Page<Product> findLowStockProducts(@Param("threshold") int threshold, Pageable pageable);

    /**
     * Aggregate query: calculates the total inventory value for a category.
     *
     * @param category the category to sum
     * @return the total value (price × stock) for the category
     */
    @Query("SELECT COALESCE(SUM(p.price.amount * p.stockQuantity), 0) " +
            "FROM Product p WHERE p.category = :category AND p.status = 'ACTIVE'")
    BigDecimal calculateInventoryValue(@Param("category") String category);
}
