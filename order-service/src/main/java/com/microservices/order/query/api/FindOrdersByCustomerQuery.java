package com.microservices.order.query.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Paged query for all orders belonging to a specific customer.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FindOrdersByCustomerQuery {

    private String customerId;
    private int page;
    private int size;
}
