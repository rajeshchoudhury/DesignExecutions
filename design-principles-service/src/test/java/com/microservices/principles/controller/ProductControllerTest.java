package com.microservices.principles.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.principles.domain.entity.ProductStatus;
import com.microservices.principles.domain.exception.ProductNotFoundException;
import com.microservices.principles.dto.request.CreateProductRequest;
import com.microservices.principles.dto.response.ProductResponse;
import com.microservices.principles.fixture.ProductFixture;
import com.microservices.principles.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@link ProductController}.
 *
 * <h3>TDD Principle — Slice Testing</h3>
 * <p>{@link WebMvcTest} loads only the web layer (controllers, exception handlers,
 * JSON serialization) — not the full application context. This makes tests fast
 * and focused on HTTP behavior.</p>
 *
 * <h3>SOC Principle — Tested in Isolation</h3>
 * <p>The service layer is mocked. These tests verify that:</p>
 * <ul>
 *   <li>Request binding works correctly</li>
 *   <li>Validation constraints are enforced</li>
 *   <li>HTTP status codes are correct</li>
 *   <li>Response JSON structure matches expectations</li>
 *   <li>Exception handler maps domain exceptions to proper HTTP responses</li>
 * </ul>
 */
@WebMvcTest(ProductController.class)
@DisplayName("ProductController")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    private static final String BASE_URL = "/api/v1/products";

    @Nested
    @DisplayName("POST /api/v1/products")
    class CreateProductEndpoint {

        @Test
        @DisplayName("should return 201 Created with valid request")
        void shouldReturn201_whenValidRequest() throws Exception {
            CreateProductRequest request = ProductFixture.aCreateRequest().build();
            UUID id = UUID.randomUUID();
            ProductResponse response = new ProductResponse(
                    id, request.name(), request.sku(), request.description(),
                    request.price(), "USD", request.stockQuantity(),
                    request.category(), ProductStatus.DRAFT, Instant.now(), Instant.now());

            given(productService.createProduct(any())).willReturn(response);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.sku").value(request.sku()))
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test
        @DisplayName("should return 400 when name is blank")
        void shouldReturn400_whenNameIsBlank() throws Exception {
            CreateProductRequest request = new CreateProductRequest(
                    "", "SKU-001", "desc", BigDecimal.TEN, "USD", 10, "Electronics");

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors").isArray());
        }

        @Test
        @DisplayName("should return 400 when price is negative")
        void shouldReturn400_whenPriceIsNegative() throws Exception {
            CreateProductRequest request = new CreateProductRequest(
                    "Product", "SKU-001", "desc", new BigDecimal("-5.00"), "USD", 10, "Electronics");

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products/{id}")
    class GetProductEndpoint {

        @Test
        @DisplayName("should return 200 with product details")
        void shouldReturn200_whenProductFound() throws Exception {
            UUID id = UUID.randomUUID();
            ProductResponse response = new ProductResponse(
                    id, "Test Product", "SKU-001", "Description",
                    new BigDecimal("99.99"), "USD", 100,
                    "Electronics", ProductStatus.ACTIVE, Instant.now(), Instant.now());

            given(productService.getProduct(id)).willReturn(response);

            mockMvc.perform(get(BASE_URL + "/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.name").value("Test Product"))
                    .andExpect(jsonPath("$.price").value(99.99));
        }

        @Test
        @DisplayName("should return 404 when product not found")
        void shouldReturn404_whenNotFound() throws Exception {
            UUID id = UUID.randomUUID();
            given(productService.getProduct(id)).willThrow(new ProductNotFoundException(id));

            mockMvc.perform(get(BASE_URL + "/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        }
    }
}
