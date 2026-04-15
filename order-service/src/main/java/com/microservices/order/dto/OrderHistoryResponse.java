package com.microservices.order.dto;

import com.microservices.order.query.projection.OrderEventRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Wraps the full event-sourcing history for a single order.
 * Each entry represents a domain event that mutated the aggregate,
 * providing a complete audit trail from creation through current state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderHistoryResponse {

    private String orderId;
    private int eventCount;
    private List<OrderEventRecord> events;

    public static OrderHistoryResponse of(String orderId, List<OrderEventRecord> events) {
        return OrderHistoryResponse.builder()
                .orderId(orderId)
                .eventCount(events.size())
                .events(events)
                .build();
    }
}
