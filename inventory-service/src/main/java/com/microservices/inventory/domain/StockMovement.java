package com.microservices.inventory.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stock_movements")
public class StockMovement {

    @Id
    private String id;

    @Indexed
    private String productId;

    private MovementType movementType;
    private int quantity;
    private int previousQuantity;
    private int newQuantity;

    @Indexed
    private String referenceId;

    private String reason;

    @CreatedDate
    private Instant createdAt;

    public enum MovementType {
        RESERVATION,
        RELEASE,
        RESTOCK,
        ADJUSTMENT
    }
}
