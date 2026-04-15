package com.microservices.strangler.modern.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.strangler.modern.domain.ModernOrder;
import com.microservices.strangler.modern.repository.ModernOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModernOrderService {

    private final ModernOrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> createOrder(Map<String, Object> request) {
        log.info("[MODERN] Creating order for customer: {}", request.get("customerId"));

        validateOrderRequest(request);

        String customerId = (String) request.get("customerId");
        List<?> items = (List<?>) request.getOrDefault("items", Collections.emptyList());
        BigDecimal totalAmount = calculateTotal(items);

        ModernOrder order = ModernOrder.builder()
                .customerId(customerId)
                .items(serializeItems(items))
                .totalAmount(totalAmount)
                .status("CREATED")
                .build();

        order = orderRepository.save(order);
        log.info("[MODERN] Order {} created successfully (total: {})", order.getOrderId(), totalAmount);

        return toMap(order);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOrder(String orderId) {
        log.info("[MODERN] Fetching order: {}", orderId);
        ModernOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        return toMap(order);
    }

    @Transactional
    public Map<String, Object> updateOrder(String orderId, Map<String, Object> updates) {
        log.info("[MODERN] Updating order: {}", orderId);

        ModernOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (updates.containsKey("status")) {
            String newStatus = (String) updates.get("status");
            validateStatusTransition(order.getStatus(), newStatus);
            order.setStatus(newStatus);
        }
        if (updates.containsKey("items")) {
            List<?> items = (List<?>) updates.get("items");
            order.setItems(serializeItems(items));
            order.setTotalAmount(calculateTotal(items));
        }

        order = orderRepository.save(order);
        log.info("[MODERN] Order {} updated successfully", orderId);
        return toMap(order);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllOrders() {
        log.info("[MODERN] Fetching all orders");
        return orderRepository.findAll().stream()
                .map(this::toMap)
                .toList();
    }

    @Transactional
    public void deleteOrder(String orderId) {
        log.info("[MODERN] Deleting order: {}", orderId);
        ModernOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (!"CREATED".equals(order.getStatus()) && !"CANCELLED".equals(order.getStatus())) {
            throw new RuntimeException("Cannot delete order in status: " + order.getStatus());
        }

        orderRepository.delete(order);
        log.info("[MODERN] Order {} deleted", orderId);
    }

    private void validateOrderRequest(Map<String, Object> request) {
        if (request.get("customerId") == null || ((String) request.get("customerId")).isBlank()) {
            throw new IllegalArgumentException("customerId is required");
        }
        Object items = request.get("items");
        if (items == null || !(items instanceof List) || ((List<?>) items).isEmpty()) {
            throw new IllegalArgumentException("At least one order item is required");
        }
    }

    private void validateStatusTransition(String currentStatus, String newStatus) {
        Map<String, Set<String>> allowedTransitions = Map.of(
                "CREATED", Set.of("APPROVED", "REJECTED", "CANCELLED"),
                "APPROVED", Set.of("PAID", "CANCELLED"),
                "PAID", Set.of("COMPLETED", "REFUNDED"),
                "COMPLETED", Set.of(),
                "REJECTED", Set.of(),
                "CANCELLED", Set.of(),
                "REFUNDED", Set.of()
        );

        Set<String> allowed = allowedTransitions.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new IllegalArgumentException(
                    String.format("Invalid status transition: %s -> %s (allowed: %s)",
                            currentStatus, newStatus, allowed));
        }
    }

    @SuppressWarnings("unchecked")
    private BigDecimal calculateTotal(List<?> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (Object item : items) {
            if (item instanceof Map) {
                Map<String, Object> itemMap = (Map<String, Object>) item;
                double price = toDouble(itemMap.get("unitPrice"));
                int qty = toInt(itemMap.get("quantity"));
                total = total.add(BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(qty)));
            }
        }
        return total;
    }

    private String serializeItems(Object items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize items: {}", e.getMessage());
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(ModernOrder order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderId", order.getOrderId());
        map.put("customerId", order.getCustomerId());
        map.put("totalAmount", order.getTotalAmount());
        map.put("status", order.getStatus());
        map.put("createdAt", order.getCreatedAt().toString());
        map.put("source", "MODERN");
        if (order.getUpdatedAt() != null) {
            map.put("updatedAt", order.getUpdatedAt().toString());
        }
        try {
            map.put("items", objectMapper.readValue(
                    order.getItems() != null ? order.getItems() : "[]", List.class));
        } catch (JsonProcessingException e) {
            map.put("items", Collections.emptyList());
        }
        return map;
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
