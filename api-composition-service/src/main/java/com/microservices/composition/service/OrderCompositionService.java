package com.microservices.composition.service;

import com.microservices.composition.client.*;
import com.microservices.composition.dto.*;
import com.microservices.composition.dto.OrderDetailsComposite.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * API COMPOSITION PATTERN IMPLEMENTATION
 *
 * This service composes data from multiple downstream microservices into unified
 * response objects. Key characteristics:
 *
 * 1. PARALLEL EXECUTION: Uses CompletableFuture.allOf() to call downstream services
 *    concurrently, minimizing total latency to the slowest service.
 *
 * 2. PARTIAL FAILURE HANDLING: If any downstream service fails, the composition still
 *    returns available data with error indicators for failed services.
 *
 * 3. CIRCUIT BREAKER: Each downstream call is protected by Resilience4j circuit breakers
 *    to prevent cascading failures.
 *
 * 4. CACHING: Frequently accessed compositions are cached with short TTLs (30s) to
 *    reduce load on downstream services.
 *
 * 5. COMPOSITION METADATA: Every response includes metadata about which services
 *    responded, individual service latencies, and any failures.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCompositionService {

    private final OrderServiceClient orderClient;
    private final PaymentServiceClient paymentClient;
    private final InventoryServiceClient inventoryClient;
    private final NotificationServiceClient notificationClient;
    private final CompositionErrorHandler errorHandler;

    @Cacheable(value = "orderDetails", key = "#orderId")
    public OrderDetailsComposite getOrderDetails(String orderId) {
        log.info("Composing order details for orderId={}", orderId);
        long compositionStart = System.currentTimeMillis();
        CompositionMetadata metadata = CompositionMetadata.builder().build();

        CompletableFuture<Map<String, Object>> orderFuture =
                errorHandler.executeAsync("order-service", metadata,
                        () -> fetchOrder(orderId));

        CompletableFuture<List<Map<String, Object>>> paymentFuture =
                errorHandler.executeAsync("payment-service", metadata,
                        () -> fetchPayments(orderId));

        CompletableFuture<List<Map<String, Object>>> notificationFuture =
                errorHandler.executeAsync("notification-service", metadata,
                        () -> fetchNotifications(orderId));

        CompletableFuture.allOf(orderFuture, paymentFuture, notificationFuture).join();

        Map<String, Object> orderData = orderFuture.join();
        List<Map<String, Object>> paymentData = paymentFuture.join();
        List<Map<String, Object>> notificationData = notificationFuture.join();

        List<InventoryItemSummary> inventoryItems = Collections.emptyList();
        if (orderData != null) {
            inventoryItems = fetchInventoryForOrder(orderData, metadata);
        }

        metadata.setTotalLatencyMs(System.currentTimeMillis() - compositionStart);
        errorHandler.logCompositionResult("getOrderDetails(" + orderId + ")", metadata);

        return OrderDetailsComposite.builder()
                .order(mapToOrderSummary(orderData))
                .payment(mapToPaymentSummary(paymentData))
                .inventoryItems(inventoryItems)
                .notifications(mapToNotificationSummaries(notificationData))
                .compositionMeta(metadata)
                .build();
    }

    @Cacheable(value = "customerDashboard", key = "#customerId")
    public CustomerDashboard getCustomerDashboard(String customerId) {
        log.info("Composing customer dashboard for customerId={}", customerId);
        long compositionStart = System.currentTimeMillis();
        CompositionMetadata metadata = CompositionMetadata.builder().build();

        CompletableFuture<List<Map<String, Object>>> ordersFuture =
                errorHandler.executeAsync("order-service", metadata,
                        () -> fetchCustomerOrders(customerId));

        CompletableFuture<List<Map<String, Object>>> notificationsFuture =
                errorHandler.executeAsync("notification-service", metadata,
                        () -> notificationClient.getNotificationsByOrder("customer-" + customerId));

        CompletableFuture.allOf(ordersFuture, notificationsFuture).join();

        List<Map<String, Object>> ordersData = ordersFuture.join();
        List<Map<String, Object>> notificationsData = notificationsFuture.join();

        List<CustomerDashboard.PaymentRecord> paymentHistory = new ArrayList<>();
        if (ordersData != null) {
            for (Map<String, Object> order : ordersData) {
                String orderId = (String) order.get("orderId");
                if (orderId != null) {
                    try {
                        List<Map<String, Object>> payments = fetchPayments(orderId);
                        if (payments != null) {
                            payments.forEach(p -> paymentHistory.add(mapToPaymentRecord(p)));
                        }
                        metadata.recordServiceSuccess("payment-service", 0);
                    } catch (Exception e) {
                        metadata.recordServiceFailure("payment-service",
                                "Failed for order " + orderId + ": " + e.getMessage());
                    }
                }
            }
        }

        metadata.setTotalLatencyMs(System.currentTimeMillis() - compositionStart);
        errorHandler.logCompositionResult("getCustomerDashboard(" + customerId + ")", metadata);

        return CustomerDashboard.builder()
                .customerId(customerId)
                .recentOrders(mapToRecentOrders(ordersData))
                .paymentHistory(paymentHistory)
                .notificationCount(notificationsData != null ? notificationsData.size() : 0)
                .compositionMeta(metadata)
                .build();
    }

    @Cacheable(value = "fulfillmentStatus", key = "#orderId")
    public FulfillmentStatus getOrderFulfillmentStatus(String orderId) {
        log.info("Composing fulfillment status for orderId={}", orderId);
        long compositionStart = System.currentTimeMillis();
        CompositionMetadata metadata = CompositionMetadata.builder().build();

        CompletableFuture<Map<String, Object>> orderFuture =
                errorHandler.executeAsync("order-service", metadata,
                        () -> fetchOrder(orderId));

        CompletableFuture<List<Map<String, Object>>> paymentFuture =
                errorHandler.executeAsync("payment-service", metadata,
                        () -> fetchPayments(orderId));

        CompletableFuture.allOf(orderFuture, paymentFuture).join();

        Map<String, Object> orderData = orderFuture.join();
        List<Map<String, Object>> paymentData = paymentFuture.join();

        String inventoryStatus = "UNKNOWN";
        if (orderData != null) {
            inventoryStatus = resolveInventoryStatus(orderData, metadata);
        }

        metadata.setTotalLatencyMs(System.currentTimeMillis() - compositionStart);
        errorHandler.logCompositionResult("getOrderFulfillmentStatus(" + orderId + ")", metadata);

        return FulfillmentStatus.builder()
                .orderId(orderId)
                .orderStatus(orderData != null ? (String) orderData.getOrDefault("status", "UNKNOWN") : "UNAVAILABLE")
                .paymentStatus(resolvePaymentStatus(paymentData))
                .inventoryStatus(inventoryStatus)
                .estimatedCompletion(estimateCompletion(orderData, paymentData))
                .compositionMeta(metadata)
                .build();
    }

    @CircuitBreaker(name = "orderService", fallbackMethod = "orderFallback")
    private Map<String, Object> fetchOrder(String orderId) {
        return orderClient.getOrder(orderId);
    }

    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    private List<Map<String, Object>> fetchPayments(String orderId) {
        return paymentClient.getPaymentsByOrder(orderId);
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "notificationFallback")
    private List<Map<String, Object>> fetchNotifications(String orderId) {
        return notificationClient.getNotificationsByOrder(orderId);
    }

    @CircuitBreaker(name = "orderService", fallbackMethod = "customerOrdersFallback")
    private List<Map<String, Object>> fetchCustomerOrders(String customerId) {
        return orderClient.getOrdersByCustomer(customerId, 0, 10);
    }

    @SuppressWarnings("unchecked")
    private List<InventoryItemSummary> fetchInventoryForOrder(Map<String, Object> orderData,
                                                              CompositionMetadata metadata) {
        List<InventoryItemSummary> items = new ArrayList<>();
        Object itemsObj = orderData.get("items");
        if (!(itemsObj instanceof List)) return items;

        List<Map<String, Object>> orderItems = (List<Map<String, Object>>) itemsObj;
        for (Map<String, Object> item : orderItems) {
            String productId = (String) item.get("productId");
            if (productId == null) continue;

            Map<String, Object> availability = errorHandler.executeWithFallback(
                    "inventory-service", metadata,
                    () -> fetchInventory(productId));

            items.add(InventoryItemSummary.builder()
                    .productId(productId)
                    .productName((String) item.getOrDefault("productName", "Unknown"))
                    .requestedQuantity(toInt(item.get("quantity")))
                    .availableQuantity(availability != null ? toInt(availability.get("availableQuantity")) : -1)
                    .reservationStatus(availability != null
                            ? (String) availability.getOrDefault("reservationStatus", "UNKNOWN")
                            : "UNAVAILABLE")
                    .build());
        }
        return items;
    }

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "inventoryFallback")
    private Map<String, Object> fetchInventory(String productId) {
        return inventoryClient.getProductAvailability(productId);
    }

    private String resolveInventoryStatus(Map<String, Object> orderData, CompositionMetadata metadata) {
        List<InventoryItemSummary> items = fetchInventoryForOrder(orderData, metadata);
        if (items.isEmpty()) return "NO_ITEMS";

        boolean allAvailable = items.stream()
                .allMatch(i -> i.getAvailableQuantity() >= i.getRequestedQuantity());
        boolean anyUnavailable = items.stream()
                .anyMatch(i -> "UNAVAILABLE".equals(i.getReservationStatus()));

        if (anyUnavailable) return "PARTIALLY_AVAILABLE";
        return allAvailable ? "FULLY_RESERVED" : "INSUFFICIENT_STOCK";
    }

    private String resolvePaymentStatus(List<Map<String, Object>> payments) {
        if (payments == null || payments.isEmpty()) return "NO_PAYMENT";
        Map<String, Object> latest = payments.get(payments.size() - 1);
        return (String) latest.getOrDefault("status", "UNKNOWN");
    }

    private Instant estimateCompletion(Map<String, Object> orderData,
                                       List<Map<String, Object>> paymentData) {
        if (orderData == null) return null;
        String status = (String) orderData.getOrDefault("status", "");
        if ("COMPLETED".equals(status)) return Instant.now();
        if ("REJECTED".equals(status) || "CANCELLED".equals(status)) return null;
        return Instant.now().plusSeconds(86400);
    }

    private OrderSummary mapToOrderSummary(Map<String, Object> data) {
        if (data == null) return null;
        return OrderSummary.builder()
                .orderId((String) data.get("orderId"))
                .customerId((String) data.get("customerId"))
                .status((String) data.get("status"))
                .totalAmount(toBigDecimal(data.get("totalAmount")))
                .items(extractItems(data))
                .createdAt(toInstant(data.get("createdAt")))
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItems(Map<String, Object> data) {
        Object items = data.get("items");
        if (items instanceof List) return (List<Map<String, Object>>) items;
        return Collections.emptyList();
    }

    private PaymentSummary mapToPaymentSummary(List<Map<String, Object>> payments) {
        if (payments == null || payments.isEmpty()) return null;
        Map<String, Object> latest = payments.get(payments.size() - 1);
        return PaymentSummary.builder()
                .paymentId((String) latest.get("paymentId"))
                .status((String) latest.get("status"))
                .amount(toBigDecimal(latest.get("amount")))
                .method((String) latest.get("method"))
                .processedAt(toInstant(latest.get("processedAt")))
                .build();
    }

    private List<NotificationSummary> mapToNotificationSummaries(List<Map<String, Object>> data) {
        if (data == null) return Collections.emptyList();
        return data.stream().map(n -> NotificationSummary.builder()
                .notificationId((String) n.get("notificationId"))
                .type((String) n.get("type"))
                .channel((String) n.get("channel"))
                .status((String) n.get("status"))
                .sentAt(toInstant(n.get("sentAt")))
                .build()).toList();
    }

    private List<CustomerDashboard.RecentOrder> mapToRecentOrders(List<Map<String, Object>> orders) {
        if (orders == null) return Collections.emptyList();
        return orders.stream().map(o -> CustomerDashboard.RecentOrder.builder()
                .orderId((String) o.get("orderId"))
                .status((String) o.get("status"))
                .totalAmount(toBigDecimal(o.get("totalAmount")))
                .itemCount(toInt(o.get("itemCount")))
                .createdAt(toInstant(o.get("createdAt")))
                .build()).toList();
    }

    private CustomerDashboard.PaymentRecord mapToPaymentRecord(Map<String, Object> p) {
        return CustomerDashboard.PaymentRecord.builder()
                .paymentId((String) p.get("paymentId"))
                .orderId((String) p.get("orderId"))
                .amount(toBigDecimal(p.get("amount")))
                .status((String) p.get("status"))
                .processedAt(toInstant(p.get("processedAt")))
                .build();
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(val.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return 0; }
    }

    private Instant toInstant(Object val) {
        if (val == null) return null;
        if (val instanceof Instant i) return i;
        try { return Instant.parse(val.toString()); } catch (Exception e) { return null; }
    }

    @SuppressWarnings("unused")
    private Map<String, Object> orderFallback(String orderId, Throwable t) {
        log.warn("Circuit breaker fallback for order-service (orderId={}): {}", orderId, t.getMessage());
        return null;
    }

    @SuppressWarnings("unused")
    private List<Map<String, Object>> paymentFallback(String orderId, Throwable t) {
        log.warn("Circuit breaker fallback for payment-service (orderId={}): {}", orderId, t.getMessage());
        return Collections.emptyList();
    }

    @SuppressWarnings("unused")
    private List<Map<String, Object>> notificationFallback(String orderId, Throwable t) {
        log.warn("Circuit breaker fallback for notification-service (orderId={}): {}", orderId, t.getMessage());
        return Collections.emptyList();
    }

    @SuppressWarnings("unused")
    private List<Map<String, Object>> customerOrdersFallback(String customerId, Throwable t) {
        log.warn("Circuit breaker fallback for order-service customer orders (customerId={}): {}",
                customerId, t.getMessage());
        return Collections.emptyList();
    }

    @SuppressWarnings("unused")
    private Map<String, Object> inventoryFallback(String productId, Throwable t) {
        log.warn("Circuit breaker fallback for inventory-service (productId={}): {}", productId, t.getMessage());
        return null;
    }
}
