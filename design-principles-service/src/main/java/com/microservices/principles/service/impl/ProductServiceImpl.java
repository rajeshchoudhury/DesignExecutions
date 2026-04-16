package com.microservices.principles.service.impl;

import com.microservices.principles.annotation.Audited;
import com.microservices.principles.domain.entity.Product;
import com.microservices.principles.domain.entity.ProductStatus;
import com.microservices.principles.domain.exception.DuplicateSkuException;
import com.microservices.principles.domain.exception.ProductNotFoundException;
import com.microservices.principles.domain.valueobject.Money;
import com.microservices.principles.dto.mapper.ProductMapper;
import com.microservices.principles.dto.request.CreateProductRequest;
import com.microservices.principles.dto.request.UpdateProductRequest;
import com.microservices.principles.dto.response.PagedResponse;
import com.microservices.principles.dto.response.ProductResponse;
import com.microservices.principles.repository.ProductRepository;
import com.microservices.principles.repository.ProductSpecifications;
import com.microservices.principles.service.ProductService;
import com.microservices.principles.service.strategy.PricingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Production implementation of {@link ProductService}.
 *
 * <h3>SOC Principle — Service Layer Responsibilities</h3>
 * <p>This class orchestrates use cases by coordinating between the repository (persistence),
 * domain entities (business rules), mappers (DTO conversion), and strategies (pricing).
 * It does <em>not</em> contain business logic (that's in the entity), nor HTTP concerns
 * (that's in the controller), nor SQL (that's in the repository).</p>
 *
 * <h3>DRY Principle</h3>
 * <ul>
 *   <li>{@code findProductOrThrow} — single lookup-or-404 method reused by every operation.</li>
 *   <li>{@code buildSpecification} — composable filter logic reused across search methods.</li>
 *   <li>{@link ProductMapper} — eliminates hand-written mapping code.</li>
 * </ul>
 *
 * <h3>KISS Principle</h3>
 * <p>Each method follows a predictable pattern: validate → load → mutate → save → map → return.
 * No deep nesting, no complex control flow.</p>
 *
 * <h3>TDD Note</h3>
 * <p>This class is designed for testability: all dependencies are injected via constructor
 * (thanks to {@link RequiredArgsConstructor}), making it trivial to mock in unit tests.</p>
 *
 * @see ProductService
 * @see Product
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final PricingStrategy pricingStrategy;

    @Override
    @Audited(operation = "CREATE_PRODUCT")
    public ProductResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateSkuException(request.sku());
        }

        Money price = Money.of(request.price(), request.currencyCode());
        Money adjustedPrice = pricingStrategy.calculatePrice(price, request.category());

        Product product = Product.create(
                request.name(),
                request.sku(),
                request.description(),
                adjustedPrice,
                request.stockQuantity(),
                request.category()
        );

        Product saved = productRepository.save(product);
        log.info("Created product: sku={}, name={}", saved.getSku(), saved.getName());

        return productMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID id) {
        return productMapper.toResponse(findProductOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductBySku(String sku) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ProductResponse> searchProducts(
            String name, String category, String status,
            BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {

        Specification<Product> spec = buildSpecification(name, category, status, minPrice, maxPrice);
        Page<Product> page = productRepository.findAll(spec, pageable);

        return PagedResponse.from(page, productMapper::toResponse);
    }

    @Override
    @Audited(operation = "UPDATE_PRODUCT")
    public ProductResponse updateProduct(UUID id, UpdateProductRequest request) {
        Product product = findProductOrThrow(id);

        product.updateDetails(request.name(), request.description(), request.category());

        Money newPrice = Money.of(request.price(), request.currencyCode());
        product.updatePrice(newPrice);

        Product saved = productRepository.save(product);
        log.info("Updated product: sku={}", saved.getSku());

        return productMapper.toResponse(saved);
    }

    @Override
    @Audited(operation = "ACTIVATE_PRODUCT")
    public ProductResponse activateProduct(UUID id) {
        Product product = findProductOrThrow(id);
        product.activate();

        Product saved = productRepository.save(product);
        log.info("Activated product: sku={}", saved.getSku());

        return productMapper.toResponse(saved);
    }

    @Override
    @Audited(operation = "DISCONTINUE_PRODUCT")
    public ProductResponse discontinueProduct(UUID id) {
        Product product = findProductOrThrow(id);
        product.discontinue();

        Product saved = productRepository.save(product);
        log.info("Discontinued product: sku={}", saved.getSku());

        return productMapper.toResponse(saved);
    }

    @Override
    @Audited(operation = "RESERVE_STOCK")
    public ProductResponse reserveStock(UUID id, int quantity) {
        Product product = findProductOrThrow(id);
        product.reserveStock(quantity);

        Product saved = productRepository.save(product);
        log.info("Reserved {} units for product: sku={}", quantity, saved.getSku());

        return productMapper.toResponse(saved);
    }

    @Override
    @Audited(operation = "RELEASE_STOCK")
    public ProductResponse releaseStock(UUID id, int quantity) {
        Product product = findProductOrThrow(id);
        product.releaseStock(quantity);

        Product saved = productRepository.save(product);
        log.info("Released {} units for product: sku={}", quantity, saved.getSku());

        return productMapper.toResponse(saved);
    }

    /**
     * DRY: single lookup method used by all operations that require an existing product.
     * Throws a domain-specific exception that the global handler translates to HTTP 404.
     */
    private Product findProductOrThrow(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    /**
     * DRY: composes JPA Specifications from optional filter parameters.
     * Only non-null parameters contribute a predicate — null parameters are ignored.
     */
    private Specification<Product> buildSpecification(
            String name, String category, String status,
            BigDecimal minPrice, BigDecimal maxPrice) {

        Specification<Product> spec = Specification.where(null);

        if (name != null && !name.isBlank()) {
            spec = spec.and(ProductSpecifications.hasNameContaining(name));
        }
        if (category != null && !category.isBlank()) {
            spec = spec.and(ProductSpecifications.hasCategory(category));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and(ProductSpecifications.hasStatus(ProductStatus.valueOf(status.toUpperCase())));
        }
        if (minPrice != null && maxPrice != null) {
            spec = spec.and(ProductSpecifications.hasPriceBetween(minPrice, maxPrice));
        }

        return spec;
    }
}
