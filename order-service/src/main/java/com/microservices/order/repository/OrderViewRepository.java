package com.microservices.order.repository;

import com.microservices.order.domain.OrderStatus;
import com.microservices.order.query.projection.OrderView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * CQRS read-side repository. Queries run against the denormalised
 * {@link OrderView} table which is populated exclusively by event handlers.
 */
@Repository
public interface OrderViewRepository extends JpaRepository<OrderView, Long> {

    Optional<OrderView> findByOrderId(String orderId);

    Page<OrderView> findByCustomerId(String customerId, Pageable pageable);

    Page<OrderView> findByStatus(OrderStatus status, Pageable pageable);

    Page<OrderView> findByCustomerIdAndStatus(String customerId, OrderStatus status, Pageable pageable);
}
