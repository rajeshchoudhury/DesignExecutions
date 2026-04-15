package com.microservices.composition.controller;

import com.microservices.common.dto.ApiResponse;
import com.microservices.composition.dto.CustomerDashboard;
import com.microservices.composition.dto.FulfillmentStatus;
import com.microservices.composition.dto.OrderDetailsComposite;
import com.microservices.composition.service.OrderCompositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/compositions")
@RequiredArgsConstructor
@Tag(name = "API Composition", description = "Aggregates data from multiple microservices into unified views")
public class CompositionController {

    private final OrderCompositionService compositionService;

    @GetMapping("/orders/{orderId}/details")
    @Operation(summary = "Get full order details composite",
            description = "Composes order, payment, inventory, and notification data from 4 services in parallel")
    public ResponseEntity<ApiResponse<OrderDetailsComposite>> getOrderDetails(
            @PathVariable String orderId) {
        log.info("API Composition request: getOrderDetails({})", orderId);
        OrderDetailsComposite composite = compositionService.getOrderDetails(orderId);
        return ResponseEntity.ok(ApiResponse.success(composite, buildCompositionMessage(composite)));
    }

    @GetMapping("/customers/{customerId}/dashboard")
    @Operation(summary = "Get customer dashboard",
            description = "Composes customer's orders, payments, and notifications into a dashboard view")
    public ResponseEntity<ApiResponse<CustomerDashboard>> getCustomerDashboard(
            @PathVariable String customerId) {
        log.info("API Composition request: getCustomerDashboard({})", customerId);
        CustomerDashboard dashboard = compositionService.getCustomerDashboard(customerId);
        return ResponseEntity.ok(ApiResponse.success(dashboard,
                String.format("Dashboard composed for customer %s: %d orders, %d payments",
                        customerId,
                        dashboard.getRecentOrders() != null ? dashboard.getRecentOrders().size() : 0,
                        dashboard.getPaymentHistory() != null ? dashboard.getPaymentHistory().size() : 0)));
    }

    @GetMapping("/orders/{orderId}/fulfillment")
    @Operation(summary = "Get order fulfillment tracking",
            description = "Composes order status, inventory reservation, and payment status into fulfillment tracking")
    public ResponseEntity<ApiResponse<FulfillmentStatus>> getOrderFulfillmentStatus(
            @PathVariable String orderId) {
        log.info("API Composition request: getOrderFulfillmentStatus({})", orderId);
        FulfillmentStatus status = compositionService.getOrderFulfillmentStatus(orderId);
        return ResponseEntity.ok(ApiResponse.success(status,
                String.format("Fulfillment status: order=%s, payment=%s, inventory=%s",
                        status.getOrderStatus(), status.getPaymentStatus(), status.getInventoryStatus())));
    }

    private String buildCompositionMessage(OrderDetailsComposite composite) {
        if (composite.getCompositionMeta() == null) return "Composition complete";
        var meta = composite.getCompositionMeta();
        if (meta.isFullyResolved()) {
            return String.format("Full composition from %d services in %dms",
                    meta.getSuccessCount(), meta.getTotalLatencyMs());
        }
        return String.format("Partial composition: %d/%d services responded (%dms). Failed: %s",
                meta.getSuccessCount(), meta.getSuccessCount() + meta.getFailureCount(),
                meta.getTotalLatencyMs(), meta.getFailedServices().keySet());
    }
}
