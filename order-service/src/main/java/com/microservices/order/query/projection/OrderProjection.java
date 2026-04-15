package com.microservices.order.query.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.order.domain.OrderStatus;
import com.microservices.order.event.*;
import com.microservices.order.query.api.*;
import com.microservices.order.repository.OrderViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CQRS read-side projection that maintains the {@link OrderView} read model.
 *
 * Each {@code @EventHandler} listens to domain events published by the
 * Order aggregate and updates the denormalised query table accordingly.
 * Each {@code @QueryHandler} serves read requests against that table.
 *
 * Because the read model is derived purely from events, it can be
 * destroyed and rebuilt at any time by replaying the event store.
 */
@Component
@ProcessingGroup("order-projection")
@RequiredArgsConstructor
@Slf4j
public class OrderProjection {

    private final OrderViewRepository repository;
    private final EventStore eventStore;
    private final ObjectMapper objectMapper;

    // ── Event Handlers (write to read model) ────────────────────────────

    @EventHandler
    public void on(OrderCreatedEvent event) {
        log.info("Projecting OrderCreatedEvent for orderId={}", event.getOrderId());

        OrderView view = OrderView.builder()
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .items(OrderItemsConverter.toJson(event.getItems()))
                .totalAmount(event.getTotalAmount())
                .status(OrderStatus.CREATED)
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getCreatedAt())
                .build();

        repository.save(view);
    }

    @EventHandler
    public void on(OrderApprovedEvent event) {
        log.info("Projecting OrderApprovedEvent for orderId={}", event.getOrderId());
        updateStatus(event.getOrderId(), OrderStatus.APPROVED, event.getApprovedAt());
    }

    @EventHandler
    public void on(OrderRejectedEvent event) {
        log.info("Projecting OrderRejectedEvent for orderId={}", event.getOrderId());
        repository.findByOrderId(event.getOrderId()).ifPresent(view -> {
            view.setStatus(OrderStatus.REJECTED);
            view.setRejectionReason(event.getReason());
            view.setUpdatedAt(event.getRejectedAt());
            repository.save(view);
        });
    }

    @EventHandler
    public void on(OrderCompletedEvent event) {
        log.info("Projecting OrderCompletedEvent for orderId={}", event.getOrderId());
        updateStatus(event.getOrderId(), OrderStatus.COMPLETED, event.getCompletedAt());
    }

    @EventHandler
    public void on(OrderCancelledEvent event) {
        log.info("Projecting OrderCancelledEvent for orderId={}", event.getOrderId());
        repository.findByOrderId(event.getOrderId()).ifPresent(view -> {
            view.setStatus(OrderStatus.CANCELLED);
            view.setRejectionReason(event.getReason());
            view.setUpdatedAt(event.getCancelledAt());
            repository.save(view);
        });
    }

    // ── Query Handlers (read from read model) ───────────────────────────

    @QueryHandler
    public Optional<OrderView> handle(FindOrderQuery query) {
        return repository.findByOrderId(query.getOrderId());
    }

    @QueryHandler
    public Page<OrderView> handle(FindOrdersByCustomerQuery query) {
        return repository.findByCustomerId(
                query.getCustomerId(),
                PageRequest.of(query.getPage(), query.getSize()));
    }

    @QueryHandler
    public Page<OrderView> handle(FindAllOrdersQuery query) {
        PageRequest pageable = PageRequest.of(query.getPage(), query.getSize());
        if (query.getStatus() != null) {
            return repository.findByStatus(query.getStatus(), pageable);
        }
        return repository.findAll(pageable);
    }

    @QueryHandler
    public List<OrderEventRecord> handle(GetOrderHistoryQuery query) {
        return eventStore.readEvents(query.getOrderId())
                .asStream()
                .map(domainEvent -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = objectMapper.convertValue(
                            domainEvent.getPayload(), Map.class);

                    return OrderEventRecord.builder()
                            .eventType(domainEvent.getPayloadType().getSimpleName())
                            .payload(payload)
                            .timestamp(domainEvent.getTimestamp())
                            .sequenceNumber(domainEvent.getSequenceNumber())
                            .build();
                })
                .toList();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private void updateStatus(String orderId, OrderStatus status, Instant updatedAt) {
        repository.findByOrderId(orderId).ifPresent(view -> {
            view.setStatus(status);
            view.setUpdatedAt(updatedAt);
            repository.save(view);
        });
    }
}
