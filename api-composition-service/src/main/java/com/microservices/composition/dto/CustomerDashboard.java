package com.microservices.composition.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDashboard {

    private String customerId;
    private List<RecentOrder> recentOrders;
    private List<PaymentRecord> paymentHistory;
    private long notificationCount;
    private CompositionMetadata compositionMeta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentOrder {
        private String orderId;
        private String status;
        private BigDecimal totalAmount;
        private int itemCount;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentRecord {
        private String paymentId;
        private String orderId;
        private BigDecimal amount;
        private String status;
        private Instant processedAt;
    }
}
