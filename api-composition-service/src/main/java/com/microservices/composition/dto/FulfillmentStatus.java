package com.microservices.composition.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FulfillmentStatus {

    private String orderId;
    private String orderStatus;
    private String paymentStatus;
    private String inventoryStatus;
    private Instant estimatedCompletion;
    private CompositionMetadata compositionMeta;
}
