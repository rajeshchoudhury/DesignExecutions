package com.microservices.principles.service;

import com.microservices.principles.dto.request.CreateProductRequest;
import com.microservices.principles.dto.request.UpdateProductRequest;
import com.microservices.principles.dto.response.PagedResponse;
import com.microservices.principles.dto.response.ProductResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Primary service interface for product management operations.
 *
 * <h3>SOC Principle — Interface Segregation</h3>
 * <p>This interface defines <em>what</em> the product service can do without revealing
 * <em>how</em> it does it. The controller depends on this interface, not on the concrete
 * implementation — enabling easy swapping (e.g., for test doubles) and enforcing that
 * the controller never reaches into service internals.</p>
 *
 * <h3>YAGNI Principle</h3>
 * <p>Methods are added to this interface only when there is a concrete endpoint or
 * consumer that requires them. We do not pre-create {@code bulkImport()},
 * {@code exportToCsv()}, or {@code cloneProduct()} because no user story asks for them.</p>
 *
 * <h3>DYC Principle</h3>
 * <p>Every method has a complete Javadoc contract specifying parameters, return values,
 * and which exceptions are thrown under which conditions.</p>
 */
public interface ProductService {

    /**
     * Creates a new product in DRAFT status.
     *
     * @param request the creation request containing product details
     * @return the created product as a response DTO
     * @throws com.microservices.principles.domain.exception.DuplicateSkuException
     *         if a product with the same SKU already exists
     */
    ProductResponse createProduct(CreateProductRequest request);

    /**
     * Retrieves a single product by its unique identifier.
     *
     * @param id the product UUID
     * @return the product response DTO
     * @throws com.microservices.principles.domain.exception.ProductNotFoundException
     *         if no product exists with the given ID
     */
    ProductResponse getProduct(UUID id);

    /**
     * Retrieves a single product by its stock-keeping unit.
     *
     * @param sku the unique SKU
     * @return the product response DTO
     * @throws com.microservices.principles.domain.exception.ProductNotFoundException
     *         if no product exists with the given SKU
     */
    ProductResponse getProductBySku(String sku);

    /**
     * Searches products with optional filtering by name, category, status, and price range.
     *
     * @param name      optional name filter (partial match, case-insensitive)
     * @param category  optional exact category filter
     * @param status    optional status filter (DRAFT, ACTIVE, DISCONTINUED)
     * @param minPrice  optional minimum price filter
     * @param maxPrice  optional maximum price filter
     * @param pageable  pagination and sorting parameters
     * @return a paginated list of matching products
     */
    PagedResponse<ProductResponse> searchProducts(
            String name, String category, String status,
            BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    /**
     * Updates a product's mutable attributes.
     *
     * @param id      the product UUID
     * @param request the update request
     * @return the updated product response DTO
     * @throws com.microservices.principles.domain.exception.ProductNotFoundException
     *         if no product exists with the given ID
     * @throws com.microservices.principles.domain.exception.InvalidProductStateException
     *         if the product is DISCONTINUED
     */
    ProductResponse updateProduct(UUID id, UpdateProductRequest request);

    /**
     * Activates a product, transitioning it from DRAFT to ACTIVE.
     *
     * @param id the product UUID
     * @return the activated product response DTO
     * @throws com.microservices.principles.domain.exception.InvalidProductStateException
     *         if the product is not in DRAFT status
     */
    ProductResponse activateProduct(UUID id);

    /**
     * Discontinues a product, preventing new orders.
     *
     * @param id the product UUID
     * @return the discontinued product response DTO
     */
    ProductResponse discontinueProduct(UUID id);

    /**
     * Reserves stock for an order.
     *
     * @param id       the product UUID
     * @param quantity the number of units to reserve
     * @return the updated product response DTO
     * @throws com.microservices.principles.domain.exception.InsufficientStockException
     *         if available stock is less than the requested quantity
     */
    ProductResponse reserveStock(UUID id, int quantity);

    /**
     * Releases previously reserved stock.
     *
     * @param id       the product UUID
     * @param quantity the number of units to release
     * @return the updated product response DTO
     */
    ProductResponse releaseStock(UUID id, int quantity);
}
