package com.microservices.strangler.modern.controller;

import com.microservices.common.dto.ApiResponse;
import com.microservices.strangler.modern.service.ModernOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v2/orders")
@RequiredArgsConstructor
@Tag(name = "Modern Orders", description = "Modern microservice order endpoints (replacement for legacy)")
public class ModernOrderController {

    private final ModernOrderService modernService;

    @PostMapping
    @Operation(summary = "Create order (MODERN)",
            description = "Creates an order using the modern microservice implementation with proper DDD design")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createOrder(
            @RequestBody Map<String, Object> request) {
        log.info("[MODERN-CONTROLLER] POST /api/v2/orders");
        Map<String, Object> order = modernService.createOrder(request);
        return ResponseEntity.ok(ApiResponse.success(order, "Order created via MODERN system"));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order (MODERN)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrder(
            @PathVariable String orderId) {
        log.info("[MODERN-CONTROLLER] GET /api/v2/orders/{}", orderId);
        Map<String, Object> order = modernService.getOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PutMapping("/{orderId}")
    @Operation(summary = "Update order (MODERN)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateOrder(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> updates) {
        log.info("[MODERN-CONTROLLER] PUT /api/v2/orders/{}", orderId);
        Map<String, Object> order = modernService.updateOrder(orderId, updates);
        return ResponseEntity.ok(ApiResponse.success(order, "Order updated via MODERN system"));
    }

    @GetMapping
    @Operation(summary = "List all orders (MODERN)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllOrders() {
        log.info("[MODERN-CONTROLLER] GET /api/v2/orders");
        List<Map<String, Object>> orders = modernService.getAllOrders();
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @DeleteMapping("/{orderId}")
    @Operation(summary = "Delete order (MODERN)")
    public ResponseEntity<ApiResponse<Void>> deleteOrder(@PathVariable String orderId) {
        log.info("[MODERN-CONTROLLER] DELETE /api/v2/orders/{}", orderId);
        modernService.deleteOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success(null, "Order deleted via MODERN system"));
    }
}
