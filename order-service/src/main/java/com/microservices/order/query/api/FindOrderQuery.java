package com.microservices.order.query.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Query to retrieve a single order by its unique identifier.
 * Handled by the OrderProjection which reads from the CQRS read model.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FindOrderQuery {

    private String orderId;
}
