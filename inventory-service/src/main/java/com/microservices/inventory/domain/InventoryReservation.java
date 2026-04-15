package com.microservices.inventory.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "inventory_reservations")
public class InventoryReservation {

    @Id
    private String id;

    @Indexed(unique = true)
    private String reservationId;

    @Indexed
    private String orderId;

    private String productId;
    private String sku;
    private int quantity;

    private ReservationStatus status;

    private Instant reservedAt;
    private Instant expiresAt;
    private Instant releasedAt;
}
