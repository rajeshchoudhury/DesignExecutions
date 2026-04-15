package com.microservices.order.controller;

import com.microservices.order.command.api.ApproveOrderCommand;
import com.microservices.order.command.api.CancelOrderCommand;
import com.microservices.order.command.api.CreateOrderCommand;
import com.microservices.order.domain.OrderItem;
import com.microservices.order.dto.CreateOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command-side REST controller (CQRS write operations).
 *
 * All mutations are dispatched as commands through the Axon {@link CommandGateway},
 * which routes them to the appropriate aggregate command handler.
 * The controller never writes to the database directly.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Order Commands", description = "Write operations for order lifecycle management")
public class OrderCommandController {

    private final CommandGateway commandGateway;

    @PostMapping
    @Operation(summary = "Create a new order", description = "Initiates the order saga orchestration")
    @ApiResponse(responseCode = "201", description = "Order created — saga started")
    public CompletableFuture<ResponseEntity<Map<String, String>>> createOrder(
            @RequestBody @Valid CreateOrderRequest request) {

        String orderId = UUID.randomUUID().toString();

        var items = request.getItems().stream()
                .map(i -> OrderItem.builder()
                        .productId(i.getProductId())
                        .productName(i.getProductName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .build())
                .toList();

        BigDecimal totalAmount = items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CreateOrderCommand command = CreateOrderCommand.builder()
                .orderId(orderId)
                .customerId(request.getCustomerId())
                .items(items)
                .totalAmount(totalAmount)
                .build();

        log.info("REST → CreateOrderCommand orderId={}, customerId={}", orderId, request.getCustomerId());

        return commandGateway.send(command)
                .thenApply(result -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(Map.of("orderId", orderId, "status", "CREATED")));
    }

    @PutMapping("/{orderId}/approve")
    @Operation(summary = "Approve an order", description = "Manually approve (for testing/admin)")
    public CompletableFuture<ResponseEntity<Map<String, String>>> approveOrder(
            @PathVariable String orderId) {

        log.info("REST → ApproveOrderCommand orderId={}", orderId);

        return commandGateway.send(ApproveOrderCommand.builder()
                        .orderId(orderId)
                        .build())
                .thenApply(result -> ResponseEntity.ok(
                        Map.of("orderId", orderId, "status", "APPROVED")));
    }

    @PutMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel an order", description = "Cancel with a reason — triggers compensation if saga is active")
    public CompletableFuture<ResponseEntity<Map<String, String>>> cancelOrder(
            @PathVariable String orderId,
            @RequestParam(defaultValue = "Customer requested cancellation") String reason) {

        log.info("REST → CancelOrderCommand orderId={}, reason={}", orderId, reason);

        return commandGateway.send(CancelOrderCommand.builder()
                        .orderId(orderId)
                        .compensationReason(reason)
                        .build())
                .thenApply(result -> ResponseEntity.ok(
                        Map.of("orderId", orderId, "status", "CANCELLED")));
    }
}
