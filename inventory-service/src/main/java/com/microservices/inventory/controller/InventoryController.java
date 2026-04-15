package com.microservices.inventory.controller;

import com.microservices.common.dto.ApiResponse;
import com.microservices.inventory.command.ReleaseInventoryCommand;
import com.microservices.inventory.command.ReserveInventoryCommand;
import com.microservices.inventory.command.RestockCommand;
import com.microservices.inventory.domain.InventoryReservation;
import com.microservices.inventory.domain.Product;
import com.microservices.inventory.dto.CreateProductRequest;
import com.microservices.inventory.dto.ProductResponse;
import com.microservices.inventory.dto.ReserveInventoryRequest;
import com.microservices.inventory.dto.StockMovementResponse;
import com.microservices.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory management — Database-per-Service (MongoDB) and Saga participant")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/reserve")
    @Operation(summary = "Reserve inventory", description = "Atomically reserves stock for an order using MongoDB findAndModify")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Inventory reserved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Insufficient stock or invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ApiResponse<InventoryReservation>> reserveInventory(
            @Valid @RequestBody ReserveInventoryRequest request) {
        InventoryReservation reservation = inventoryService.reserveInventory(
                ReserveInventoryCommand.builder()
                        .orderId(request.getOrderId())
                        .productId(request.getProductId())
                        .sku(request.getSku())
                        .quantity(request.getQuantity())
                        .build());
        return ResponseEntity.ok(ApiResponse.success(reservation, "Inventory reserved successfully"));
    }

    @PostMapping("/release")
    @Operation(summary = "Release reservation", description = "Releases a reservation and restores available stock (saga compensation)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reservation released successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Reservation not found")
    })
    public ResponseEntity<ApiResponse<Void>> releaseInventory(
            @RequestParam(required = false) String reservationId,
            @RequestParam(required = false) String orderId,
            @RequestParam(defaultValue = "Manual release") String reason) {
        inventoryService.releaseInventory(
                ReleaseInventoryCommand.builder()
                        .reservationId(reservationId)
                        .orderId(orderId)
                        .reason(reason)
                        .build());
        return ResponseEntity.ok(ApiResponse.success(null, "Inventory released successfully"));
    }

    @PostMapping("/restock")
    @Operation(summary = "Restock product", description = "Increases available quantity for a product")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Product restocked successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ResponseEntity<ApiResponse<ProductResponse>> restockInventory(
            @RequestParam String productId,
            @RequestParam int quantity,
            @RequestParam(defaultValue = "Manual restock") String reason) {
        Product product = inventoryService.restockInventory(
                RestockCommand.builder()
                        .productId(productId)
                        .quantity(quantity)
                        .reason(reason)
                        .build());
        return ResponseEntity.ok(ApiResponse.success(toResponse(product), "Product restocked successfully"));
    }

    @GetMapping("/products/{productId}")
    @Operation(summary = "Get product availability", description = "Returns current available and reserved quantities")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Product details returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ResponseEntity<ApiResponse<ProductResponse>> getProductAvailability(
            @Parameter(description = "Product ID") @PathVariable String productId) {
        ProductResponse response = inventoryService.getProductAvailability(productId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/products/{productId}/movements")
    @Operation(summary = "Get stock movement history", description = "Returns audit trail of all stock changes for a product")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Movement history returned")
    })
    public ResponseEntity<ApiResponse<List<StockMovementResponse>>> getStockMovements(
            @Parameter(description = "Product ID") @PathVariable String productId) {
        List<StockMovementResponse> movements = inventoryService.getStockMovements(productId);
        return ResponseEntity.ok(ApiResponse.success(movements));
    }

    @GetMapping("/products/low-stock")
    @Operation(summary = "Get low stock products", description = "Returns products with available quantity at or below reorder threshold")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Low stock products returned")
    })
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getLowStockProducts() {
        List<ProductResponse> products = inventoryService.getLowStockProducts();
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @PostMapping("/products")
    @Operation(summary = "Create new product", description = "Creates a new product in the inventory catalog")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Product created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        Product product = inventoryService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toResponse(product), "Product created successfully"));
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .availableQuantity(product.getAvailableQuantity())
                .reservedQuantity(product.getReservedQuantity())
                .unitPrice(product.getUnitPrice())
                .warehouse(product.getWarehouse())
                .build();
    }
}
