package com.microservices.strangler.legacy;

import com.microservices.common.dto.ApiResponse;
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
@RequestMapping("/api/legacy/orders")
@RequiredArgsConstructor
@Deprecated
@Tag(name = "Legacy Orders", description = "Legacy monolith order endpoints (deprecated - use /api/v2/orders)")
public class LegacyOrderController {

    private final LegacyOrderService legacyService;

    @PostMapping
    @Operation(summary = "Create order (LEGACY)", description = "Deprecated: uses legacy monolith path")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createOrder(
            @RequestBody Map<String, Object> request) {
        log.warn("[LEGACY-CONTROLLER] POST /api/legacy/orders - deprecated endpoint");
        Map<String, Object> order = legacyService.createOrder(request);
        return ResponseEntity.ok(ApiResponse.success(order, "Order created via LEGACY system"));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order (LEGACY)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrder(
            @PathVariable String orderId) {
        log.warn("[LEGACY-CONTROLLER] GET /api/legacy/orders/{} - deprecated endpoint", orderId);
        Map<String, Object> order = legacyService.getOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PutMapping("/{orderId}")
    @Operation(summary = "Update order (LEGACY)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateOrder(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> updates) {
        log.warn("[LEGACY-CONTROLLER] PUT /api/legacy/orders/{} - deprecated endpoint", orderId);
        Map<String, Object> order = legacyService.updateOrder(orderId, updates);
        return ResponseEntity.ok(ApiResponse.success(order, "Order updated via LEGACY system"));
    }

    @GetMapping
    @Operation(summary = "List all orders (LEGACY)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllOrders() {
        log.warn("[LEGACY-CONTROLLER] GET /api/legacy/orders - deprecated endpoint");
        List<Map<String, Object>> orders = legacyService.getAllOrders();
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @DeleteMapping("/{orderId}")
    @Operation(summary = "Delete order (LEGACY)")
    public ResponseEntity<ApiResponse<Void>> deleteOrder(@PathVariable String orderId) {
        log.warn("[LEGACY-CONTROLLER] DELETE /api/legacy/orders/{} - deprecated endpoint", orderId);
        legacyService.deleteOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success(null, "Order deleted via LEGACY system"));
    }

    @PostMapping("/{orderId}/payment")
    @Operation(summary = "Process payment (LEGACY)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> processPayment(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> paymentRequest) {
        log.warn("[LEGACY-CONTROLLER] POST /api/legacy/orders/{}/payment - deprecated endpoint", orderId);
        Map<String, Object> payment = legacyService.processPayment(orderId, paymentRequest);
        return ResponseEntity.ok(ApiResponse.success(payment, "Payment processed via LEGACY system"));
    }
}
