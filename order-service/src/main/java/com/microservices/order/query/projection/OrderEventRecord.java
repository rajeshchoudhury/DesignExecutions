package com.microservices.order.query.projection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Lightweight DTO representing a single event from the event store.
 * Returned by the {@code GetOrderHistoryQuery} to expose the full audit trail
 * of an order's state changes — a direct benefit of Event Sourcing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEventRecord {

    private String eventType;
    private Map<String, Object> payload;
    private Instant timestamp;
    private long sequenceNumber;
}
