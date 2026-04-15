package com.microservices.inventory.service;

import com.microservices.inventory.event.InventoryReleasedEvent;
import com.microservices.inventory.event.InventoryReservationFailedEvent;
import com.microservices.inventory.event.InventoryReservedEvent;
import com.microservices.inventory.event.LowStockEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryEventPublisher {

    private static final String INVENTORY_EVENTS_TOPIC = "inventory-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishInventoryReserved(InventoryReservedEvent event) {
        log.info("Publishing InventoryReservedEvent for order={}, reservation={}",
                event.getOrderId(), event.getReservationId());
        publish(event.getOrderId(), event);
    }

    public void publishInventoryReservationFailed(InventoryReservationFailedEvent event) {
        log.warn("Publishing InventoryReservationFailedEvent for order={}, reason={}",
                event.getOrderId(), event.getReason());
        publish(event.getOrderId(), event);
    }

    public void publishInventoryReleased(InventoryReleasedEvent event) {
        log.info("Publishing InventoryReleasedEvent for order={}, reservation={}",
                event.getOrderId(), event.getReservationId());
        publish(event.getOrderId(), event);
    }

    public void publishLowStock(LowStockEvent event) {
        log.warn("Publishing LowStockEvent for product={}, sku={}, quantity={}",
                event.getProductId(), event.getSku(), event.getCurrentQuantity());
        publish(event.getProductId(), event);
    }

    private void publish(String key, Object event) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(INVENTORY_EVENTS_TOPIC, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event {} with key={}: {}",
                        event.getClass().getSimpleName(), key, ex.getMessage(), ex);
            } else {
                log.debug("Event {} published successfully to partition={} offset={}",
                        event.getClass().getSimpleName(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
