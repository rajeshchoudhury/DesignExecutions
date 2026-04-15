package com.microservices.order.query.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Query that returns the full event-sourcing history for an order.
 * Demonstrates event replay — each event that ever mutated this aggregate
 * is returned in sequence order, giving a complete audit trail.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetOrderHistoryQuery {

    private String orderId;
}
