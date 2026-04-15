package com.microservices.strangler.modern.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "modern_orders", indexes = {
        @Index(name = "idx_modern_order_customer_id", columnList = "customerId"),
        @Index(name = "idx_modern_order_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModernOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    @Builder.Default
    private String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);

    @Column(nullable = false)
    private String customerId;

    @Column(columnDefinition = "TEXT")
    private String items;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    @Builder.Default
    private String status = "CREATED";

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (orderId == null) {
            orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
