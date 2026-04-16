package com.microservices.principles.controller;

import com.microservices.principles.dto.request.CreateProductRequest;
import com.microservices.principles.dto.request.StockAdjustmentRequest;
import com.microservices.principles.dto.request.UpdateProductRequest;
import com.microservices.principles.dto.response.ApiErrorResponse;
import com.microservices.principles.dto.response.PagedResponse;
import com.microservices.principles.dto.response.ProductResponse;
import com.microservices.principles.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

/**
 * REST controller for product management operations.
 *
 * <h3>SOC Principle — Controller Responsibilities</h3>
 * <p>This controller handles <em>only</em> HTTP concerns:</p>
 * <ul>
 *   <li>Request binding and validation trigger (via {@code @Valid})</li>
 *   <li>HTTP status code selection (201 Created, 200 OK, etc.)</li>
 *   <li>Content negotiation and response serialization</li>
 *   <li>OpenAPI documentation annotations</li>
 * </ul>
 * <p>It delegates <strong>all</strong> business logic to {@link ProductService}.</p>
 *
 * <h3>DYC Principle — Self-Documenting API</h3>
 * <p>Every endpoint is annotated with {@link Operation}, {@link ApiResponse}, and
 * {@link Parameter} so that Swagger UI renders a complete, interactive API reference
 * at {@code /swagger-ui.html}. This is <em>living documentation</em> — it cannot drift
 * from the implementation.</p>
 *
 * <h3>KISS Principle</h3>
 * <p>No complex routing, no content negotiation tricks, no manual JSON parsing.
 * Standard Spring MVC annotations handle everything.</p>
 *
 * @see ProductService
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Product Management", description = "CRUD and lifecycle operations for products")
public class ProductController {

    private final ProductService productService;

    /**
     * Creates a new product in DRAFT status.
     *
     * @param request the product creation payload
     * @return 201 Created with the new product and a Location header
     */
    @PostMapping
    @Operation(summary = "Create a new product", description = "Creates a product in DRAFT status. "
            + "The product must be activated via PUT /activate before it becomes visible to customers.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Duplicate SKU",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        URI location = URI.create("/api/v1/products/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Retrieves a product by its unique identifier.
     *
     * @param id the product UUID
     * @return 200 OK with the product details
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ProductResponse> getProduct(
            @Parameter(description = "Product UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    /**
     * Retrieves a product by its unique SKU.
     *
     * @param sku the stock-keeping unit
     * @return 200 OK with the product details
     */
    @GetMapping("/sku/{sku}")
    @Operation(summary = "Get product by SKU")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ResponseEntity<ProductResponse> getProductBySku(
            @Parameter(description = "Stock-keeping unit") @PathVariable String sku) {
        return ResponseEntity.ok(productService.getProductBySku(sku));
    }

    /**
     * Searches products with optional filters and pagination.
     *
     * @param name     optional name filter (partial match)
     * @param category optional category filter (exact match)
     * @param status   optional status filter
     * @param minPrice optional minimum price
     * @param maxPrice optional maximum price
     * @param pageable pagination parameters (page, size, sort)
     * @return 200 OK with paginated product list
     */
    @GetMapping
    @Operation(summary = "Search products", description = "Supports filtering by name, category, "
            + "status, and price range. All filters are optional and combined with AND logic.")
    public ResponseEntity<PagedResponse<ProductResponse>> searchProducts(
            @Parameter(description = "Filter by name (partial, case-insensitive)")
            @RequestParam(required = false) String name,

            @Parameter(description = "Filter by exact category")
            @RequestParam(required = false) String category,

            @Parameter(description = "Filter by status: DRAFT, ACTIVE, DISCONTINUED")
            @RequestParam(required = false) String status,

            @Parameter(description = "Minimum price filter")
            @RequestParam(required = false) BigDecimal minPrice,

            @Parameter(description = "Maximum price filter")
            @RequestParam(required = false) BigDecimal maxPrice,

            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                productService.searchProducts(name, category, status, minPrice, maxPrice, pageable));
    }

    /**
     * Updates a product's mutable attributes.
     *
     * @param id      the product UUID
     * @param request the update payload
     * @return 200 OK with the updated product
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update product details")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product updated"),
            @ApiResponse(responseCode = "404", description = "Product not found"),
            @ApiResponse(responseCode = "422", description = "Invalid state for update")
    })
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    /**
     * Activates a DRAFT product, making it available for purchase.
     *
     * @param id the product UUID
     * @return 200 OK with the activated product
     */
    @PutMapping("/{id}/activate")
    @Operation(summary = "Activate a draft product")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product activated"),
            @ApiResponse(responseCode = "422", description = "Product is not in DRAFT status")
    })
    public ResponseEntity<ProductResponse> activateProduct(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.activateProduct(id));
    }

    /**
     * Discontinues a product, preventing new orders.
     *
     * @param id the product UUID
     * @return 200 OK with the discontinued product
     */
    @PutMapping("/{id}/discontinue")
    @Operation(summary = "Discontinue a product")
    public ResponseEntity<ProductResponse> discontinueProduct(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.discontinueProduct(id));
    }

    /**
     * Reserves stock for an order.
     *
     * @param id      the product UUID
     * @param request stock adjustment details
     * @return 200 OK with the updated product
     */
    @PostMapping("/{id}/reserve-stock")
    @Operation(summary = "Reserve product stock for an order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock reserved"),
            @ApiResponse(responseCode = "409", description = "Insufficient stock")
    })
    public ResponseEntity<ProductResponse> reserveStock(
            @PathVariable UUID id,
            @Valid @RequestBody StockAdjustmentRequest request) {
        return ResponseEntity.ok(productService.reserveStock(id, request.quantity()));
    }

    /**
     * Releases previously reserved stock.
     *
     * @param id      the product UUID
     * @param request stock adjustment details
     * @return 200 OK with the updated product
     */
    @PostMapping("/{id}/release-stock")
    @Operation(summary = "Release reserved stock")
    public ResponseEntity<ProductResponse> releaseStock(
            @PathVariable UUID id,
            @Valid @RequestBody StockAdjustmentRequest request) {
        return ResponseEntity.ok(productService.releaseStock(id, request.quantity()));
    }
}
