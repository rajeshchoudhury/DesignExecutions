package com.microservices.inventory.dto;

import com.microservices.inventory.domain.StockMovement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementResponse {
    private StockMovement.MovementType movementType;
    private int quantity;
    private int previousQuantity;
    private int newQuantity;
    private String referenceId;
    private Instant createdAt;
}
