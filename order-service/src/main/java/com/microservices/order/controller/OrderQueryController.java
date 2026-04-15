package com.microservices.order.controller;

import com.microservices.order.domain.OrderStatus;
import com.microservices.order.dto.OrderHistoryResponse;
import com.microservices.order.dto.OrderResponse;
import com.microservices.order.query.api.*;
import com.microservices.order.query.projection.OrderEventRecord;
import com.microservices.order.query.projection.OrderView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Query-side REST controller (CQRS read operations).
 *
 * All reads are dispatched through the Axon {@link QueryGateway}, which routes
 * them to {@code @QueryHandler} methods in the OrderProjection. This enforces
 * strict separation between the write model (aggregate) and the read model.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Order Queries", description = "Read operations for order data retrieval")
public class OrderQueryController {

    private final QueryGateway queryGateway;

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID", description = "Reads from the CQRS query-side projection")
    public CompletableFuture<ResponseEntity<OrderResponse>> getOrder(@PathVariable String orderId) {
        log.debug("REST → FindOrderQuery orderId={}", orderId);

        return queryGateway.query(
                        new FindOrderQuery(orderId),
                        ResponseTypes.optionalInstanceOf(OrderView.class))
                .thenApply(opt -> opt
                        .map(OrderResponse::fromView)
                        .map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build()));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Get orders by customer", description = "Paged list of a customer's orders")
    public CompletableFuture<Page<OrderResponse>> getOrdersByCustomer(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("REST → FindOrdersByCustomerQuery customerId={}", customerId);

        return queryGateway.query(
                        new FindOrdersByCustomerQuery(customerId, page, size),
                        new org.axonframework.messaging.responsetypes.ResponseType<Page<OrderView>>() {
                            @Override
                            public boolean matches(java.lang.reflect.Type responseType) { return true; }

                            @Override
                            @SuppressWarnings("unchecked")
                            public Page<OrderView> convert(Object initial) { return (Page<OrderView>) initial; }

                            @Override
                            public Class<Page<OrderView>> expectedResponseType() {
                                return null;
                            }
                        })
                .thenApply(p -> p.map(OrderResponse::fromView));
    }

    @GetMapping
    @Operation(summary = "List all orders", description = "Paged list with optional status filter")
    public CompletableFuture<Page<OrderResponse>> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status) {

        log.debug("REST → FindAllOrdersQuery page={}, size={}, status={}", page, size, status);

        return queryGateway.query(
                        new FindAllOrdersQuery(page, size, status),
                        new org.axonframework.messaging.responsetypes.ResponseType<Page<OrderView>>() {
                            @Override
                            public boolean matches(java.lang.reflect.Type responseType) { return true; }

                            @Override
                            @SuppressWarnings("unchecked")
                            public Page<OrderView> convert(Object initial) { return (Page<OrderView>) initial; }

                            @Override
                            public Class<Page<OrderView>> expectedResponseType() {
                                return null;
                            }
                        })
                .thenApply(p -> p.map(OrderResponse::fromView));
    }

    @GetMapping("/{orderId}/history")
    @Operation(summary = "Get order event history",
            description = "Returns the full event-sourcing audit trail for an order")
    public CompletableFuture<ResponseEntity<OrderHistoryResponse>> getOrderHistory(
            @PathVariable String orderId) {

        log.debug("REST → GetOrderHistoryQuery orderId={}", orderId);

        return queryGateway.query(
                        new GetOrderHistoryQuery(orderId),
                        ResponseTypes.multipleInstancesOf(OrderEventRecord.class))
                .thenApply(events -> ResponseEntity.ok(
                        OrderHistoryResponse.of(orderId, events)));
    }
}
