package com.microservices.strangler.legacy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * LEGACY MONOLITH SERVICE - Deliberately uses anti-patterns.
 *
 * This class simulates a typical legacy monolith that has grown organically over time.
 * Anti-patterns demonstrated:
 * - God class: order processing, payment, inventory, and notifications all in one class
 * - No separation of concerns: business logic, data access, and notification all mixed
 * - Direct JDBC-style data management via in-memory maps (simulating raw SQL queries)
 * - Thread.sleep to simulate the slow performance of legacy systems
 * - No proper error handling or validation
 * - No dependency injection for sub-components
 *
 * @deprecated Migrate to ModernOrderService. See StranglerFacade for routing config.
 */
@Slf4j
@Service
@Deprecated(since = "2.0", forRemoval = true)
public class LegacyOrderService {

    private final Map<String, Map<String, Object>> orderStore = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> paymentStore = new LinkedHashMap<>();
    private final Map<String, Integer> inventoryStore = new LinkedHashMap<>();

    public LegacyOrderService() {
        inventoryStore.put("PROD-001", 100);
        inventoryStore.put("PROD-002", 50);
        inventoryStore.put("PROD-003", 200);
    }

    /**
     * Creates an order. In the legacy system, this does everything:
     * validates, persists, reserves inventory, and sends notification - all synchronously.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createOrder(Map<String, Object> request) {
        log.warn("[LEGACY] createOrder called - this path is deprecated");
        simulateSlowLegacyBehavior(200);

        String orderId = "LEGACY-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderId", orderId);
        order.put("customerId", request.getOrDefault("customerId", "unknown"));
        order.put("items", request.getOrDefault("items", Collections.emptyList()));
        order.put("status", "CREATED");
        order.put("createdAt", Instant.now().toString());
        order.put("source", "LEGACY");

        BigDecimal total = BigDecimal.ZERO;
        Object itemsObj = request.get("items");
        if (itemsObj instanceof List) {
            for (Object item : (List<?>) itemsObj) {
                if (item instanceof Map) {
                    Map<String, Object> itemMap = (Map<String, Object>) item;
                    double price = toDouble(itemMap.get("unitPrice"));
                    int qty = toInt(itemMap.get("quantity"));
                    total = total.add(BigDecimal.valueOf(price * qty));
                }
            }
        }
        order.put("totalAmount", total);

        simulateSlowLegacyBehavior(100);
        orderStore.put(orderId, order);

        checkInventory(request);
        sendNotification(orderId, "ORDER_CREATED", "Order " + orderId + " created in legacy system");

        log.warn("[LEGACY] Order {} created via legacy path (total: {})", orderId, total);
        return order;
    }

    public Map<String, Object> getOrder(String orderId) {
        log.warn("[LEGACY] getOrder called for {} - this path is deprecated", orderId);
        simulateSlowLegacyBehavior(150);
        Map<String, Object> order = orderStore.get(orderId);
        if (order == null) {
            throw new RuntimeException("Legacy: Order not found: " + orderId);
        }
        return order;
    }

    public Map<String, Object> updateOrder(String orderId, Map<String, Object> updates) {
        log.warn("[LEGACY] updateOrder called for {} - this path is deprecated", orderId);
        simulateSlowLegacyBehavior(180);

        Map<String, Object> order = orderStore.get(orderId);
        if (order == null) {
            throw new RuntimeException("Legacy: Order not found: " + orderId);
        }

        updates.forEach((key, value) -> {
            if (!"orderId".equals(key) && !"createdAt".equals(key)) {
                order.put(key, value);
            }
        });
        order.put("updatedAt", Instant.now().toString());
        order.put("source", "LEGACY");

        sendNotification(orderId, "ORDER_UPDATED", "Order " + orderId + " updated in legacy system");

        return order;
    }

    public List<Map<String, Object>> getAllOrders() {
        log.warn("[LEGACY] getAllOrders called - this path is deprecated");
        simulateSlowLegacyBehavior(300);
        return new ArrayList<>(orderStore.values());
    }

    public void deleteOrder(String orderId) {
        log.warn("[LEGACY] deleteOrder called for {} - this path is deprecated", orderId);
        simulateSlowLegacyBehavior(100);
        orderStore.remove(orderId);
    }

    public Map<String, Object> processPayment(String orderId, Map<String, Object> paymentRequest) {
        log.warn("[LEGACY] processPayment called for {} - this path is deprecated", orderId);
        simulateSlowLegacyBehavior(500);

        String paymentId = "LEGPAY-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("paymentId", paymentId);
        payment.put("orderId", orderId);
        payment.put("amount", paymentRequest.getOrDefault("amount", 0));
        payment.put("status", "PROCESSED");
        payment.put("processedAt", Instant.now().toString());
        payment.put("source", "LEGACY");

        paymentStore.put(paymentId, payment);

        Map<String, Object> order = orderStore.get(orderId);
        if (order != null) {
            order.put("status", "PAID");
            order.put("paymentId", paymentId);
        }

        sendNotification(orderId, "PAYMENT_PROCESSED",
                "Payment " + paymentId + " processed for order " + orderId);

        return payment;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> checkInventory(Map<String, Object> request) {
        log.warn("[LEGACY] checkInventory called - this path is deprecated");
        simulateSlowLegacyBehavior(250);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "LEGACY");
        List<Map<String, Object>> itemResults = new ArrayList<>();

        Object itemsObj = request.get("items");
        if (itemsObj instanceof List) {
            for (Object item : (List<?>) itemsObj) {
                if (item instanceof Map) {
                    Map<String, Object> itemMap = (Map<String, Object>) item;
                    String productId = (String) itemMap.get("productId");
                    int requested = toInt(itemMap.get("quantity"));
                    int available = inventoryStore.getOrDefault(productId, 0);

                    Map<String, Object> itemResult = new LinkedHashMap<>();
                    itemResult.put("productId", productId);
                    itemResult.put("requested", requested);
                    itemResult.put("available", available);
                    itemResult.put("sufficient", available >= requested);
                    itemResults.add(itemResult);
                }
            }
        }
        result.put("items", itemResults);
        return result;
    }

    public void sendNotification(String orderId, String type, String message) {
        log.warn("[LEGACY] sendNotification called - this path is deprecated");
        simulateSlowLegacyBehavior(100);
        log.info("[LEGACY-NOTIFICATION] orderId={}, type={}, message={}", orderId, type, message);
    }

    private void simulateSlowLegacyBehavior(long baseDelayMs) {
        try {
            long delay = baseDelayMs + (long) (Math.random() * 100);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0.0; }
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return 0; }
    }
}
