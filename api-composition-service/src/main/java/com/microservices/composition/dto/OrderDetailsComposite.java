package com.microservices.composition.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailsComposite {

    private OrderSummary order;
    private PaymentSummary payment;
    private List<InventoryItemSummary> inventoryItems;
    private List<NotificationSummary> notifications;
    private CompositionMetadata compositionMeta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderSummary {
        private String orderId;
        private String customerId;
        private String status;
        private BigDecimal totalAmount;
        private List<Map<String, Object>> items;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSummary {
        private String paymentId;
        private String status;
        private BigDecimal amount;
        private String method;
        private Instant processedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryItemSummary {
        private String productId;
        private String productName;
        private int requestedQuantity;
        private int availableQuantity;
        private String reservationStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationSummary {
        private String notificationId;
        private String type;
        private String channel;
        private String status;
        private Instant sentAt;
    }
}
