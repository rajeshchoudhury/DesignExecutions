package com.microservices.inventory.service;

import com.microservices.common.events.order.OrderCreatedEvent;
import com.microservices.inventory.command.ReleaseInventoryCommand;
import com.microservices.inventory.command.ReserveInventoryCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryKafkaConsumer {

    private final InventoryService inventoryService;

    @KafkaListener(topics = "order-events", groupId = "inventory-service-group")
    public void handleOrderEvent(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        log.info("Received order event: type={}, key={}", event.getClass().getSimpleName(), record.key());

        try {
            if (event instanceof OrderCreatedEvent orderCreated) {
                handleOrderCreated(orderCreated);
            } else if (isOrderCancelledEvent(event)) {
                handleOrderCancelled(record.key(), event);
            } else {
                log.debug("Ignoring unhandled event type: {}", event.getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.error("Error processing order event: type={}, key={}, error={}",
                    event.getClass().getSimpleName(), record.key(), e.getMessage(), e);
        }
    }

    private void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Processing OrderCreatedEvent: orderId={}, items={}", event.getOrderId(), event.getItems().size());

        event.getItems().forEach(item -> {
            try {
                inventoryService.reserveInventory(
                        ReserveInventoryCommand.builder()
                                .orderId(event.getOrderId())
                                .productId(item.getProductId())
                                .sku(item.getProductId())
                                .quantity(item.getQuantity())
                                .build());
            } catch (Exception e) {
                log.error("Failed to reserve inventory for orderId={}, productId={}: {}",
                        event.getOrderId(), item.getProductId(), e.getMessage());
            }
        });
    }

    private void handleOrderCancelled(String orderId, Object event) {
        log.info("Processing order cancellation: orderId={}", orderId);

        try {
            inventoryService.releaseInventory(
                    ReleaseInventoryCommand.builder()
                            .orderId(orderId)
                            .reason("Order cancelled")
                            .build());
        } catch (Exception e) {
            log.error("Failed to release inventory for cancelled order={}: {}", orderId, e.getMessage());
        }
    }

    /**
     * Checks for order cancellation events by class name to avoid tight coupling
     * on a specific event class that may not be in the common-lib yet.
     */
    private boolean isOrderCancelledEvent(Object event) {
        String className = event.getClass().getSimpleName();
        return className.contains("OrderCancelled") || className.contains("OrderRejected");
    }
}
