package com.microservices.order.query.api;

import com.microservices.order.domain.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Paged query for all orders, with optional status filter.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FindAllOrdersQuery {

    private int page;
    private int size;
    private OrderStatus status;
}
