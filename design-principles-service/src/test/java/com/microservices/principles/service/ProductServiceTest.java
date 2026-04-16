package com.microservices.principles.service;

import com.microservices.principles.domain.entity.Product;
import com.microservices.principles.domain.entity.ProductStatus;
import com.microservices.principles.domain.exception.DuplicateSkuException;
import com.microservices.principles.domain.exception.ProductNotFoundException;
import com.microservices.principles.domain.valueobject.Money;
import com.microservices.principles.dto.mapper.ProductMapper;
import com.microservices.principles.dto.request.CreateProductRequest;
import com.microservices.principles.dto.response.ProductResponse;
import com.microservices.principles.fixture.ProductFixture;
import com.microservices.principles.repository.ProductRepository;
import com.microservices.principles.service.impl.ProductServiceImpl;
import com.microservices.principles.service.strategy.PricingStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProductServiceImpl}.
 *
 * <h3>TDD Principle — Isolation</h3>
 * <p>These tests use Mockito to completely isolate the service from its dependencies
 * (repository, mapper, pricing strategy). This means:</p>
 * <ul>
 *   <li>No database required</li>
 *   <li>No Spring context loaded</li>
 *   <li>Sub-millisecond execution time per test</li>
 *   <li>Failures pinpoint service logic, not infrastructure</li>
 * </ul>
 *
 * <h3>TDD Principle — BDD Style</h3>
 * <p>Tests use Mockito's BDD API ({@code given/when/then}) for readability:
 * {@code given(repo.findById(id)).willReturn(Optional.of(product))}</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private PricingStrategy pricingStrategy;

    @InjectMocks
    private ProductServiceImpl productService;

    @Nested
    @DisplayName("createProduct")
    class CreateProduct {

        @Test
        @DisplayName("should create product and return response")
        void shouldCreateProduct_whenSkuIsUnique() {
            CreateProductRequest request = ProductFixture.aCreateRequest().build();
            Product savedProduct = ProductFixture.aProduct().build();
            ProductResponse expectedResponse = createMockResponse(savedProduct);

            given(productRepository.existsBySku(request.sku())).willReturn(false);
            given(pricingStrategy.calculatePrice(any(Money.class), eq(request.category())))
                    .willAnswer(inv -> inv.getArgument(0));
            given(productRepository.save(any(Product.class))).willReturn(savedProduct);
            given(productMapper.toResponse(savedProduct)).willReturn(expectedResponse);

            ProductResponse result = productService.createProduct(request);

            assertThat(result).isEqualTo(expectedResponse);
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("should throw DuplicateSkuException when SKU exists")
        void shouldThrow_whenSkuAlreadyExists() {
            CreateProductRequest request = ProductFixture.aCreateRequest().build();
            given(productRepository.existsBySku(request.sku())).willReturn(true);

            assertThatThrownBy(() -> productService.createProduct(request))
                    .isInstanceOf(DuplicateSkuException.class)
                    .hasMessageContaining(request.sku());

            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getProduct")
    class GetProduct {

        @Test
        @DisplayName("should return product when found")
        void shouldReturnProduct_whenFound() {
            UUID id = UUID.randomUUID();
            Product product = ProductFixture.aProduct().build();
            ProductResponse expectedResponse = createMockResponse(product);

            given(productRepository.findById(id)).willReturn(Optional.of(product));
            given(productMapper.toResponse(product)).willReturn(expectedResponse);

            ProductResponse result = productService.getProduct(id);

            assertThat(result).isEqualTo(expectedResponse);
        }

        @Test
        @DisplayName("should throw ProductNotFoundException when not found")
        void shouldThrow_whenNotFound() {
            UUID id = UUID.randomUUID();
            given(productRepository.findById(id)).willReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProduct(id))
                    .isInstanceOf(ProductNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("activateProduct")
    class ActivateProduct {

        @Test
        @DisplayName("should activate draft product")
        void shouldActivateDraftProduct() {
            UUID id = UUID.randomUUID();
            Product product = ProductFixture.aProduct().build();
            ProductResponse expectedResponse = createMockResponse(product);

            given(productRepository.findById(id)).willReturn(Optional.of(product));
            given(productRepository.save(product)).willReturn(product);
            given(productMapper.toResponse(product)).willReturn(expectedResponse);

            ProductResponse result = productService.activateProduct(id);

            assertThat(result).isEqualTo(expectedResponse);
            assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("reserveStock")
    class ReserveStock {

        @Test
        @DisplayName("should reserve stock when sufficient")
        void shouldReserveStock_whenSufficient() {
            UUID id = UUID.randomUUID();
            Product product = ProductFixture.aProduct().withStockQuantity(50).build();
            ProductResponse expectedResponse = createMockResponse(product);

            given(productRepository.findById(id)).willReturn(Optional.of(product));
            given(productRepository.save(product)).willReturn(product);
            given(productMapper.toResponse(product)).willReturn(expectedResponse);

            productService.reserveStock(id, 30);

            assertThat(product.getStockQuantity()).isEqualTo(20);
            verify(productRepository).save(product);
        }
    }

    private ProductResponse createMockResponse(Product product) {
        return new ProductResponse(
                UUID.randomUUID(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                product.getPrice().getAmount(),
                product.getPrice().getCurrencyCode(),
                product.getStockQuantity(),
                product.getCategory(),
                product.getStatus(),
                Instant.now(),
                Instant.now()
        );
    }
}
