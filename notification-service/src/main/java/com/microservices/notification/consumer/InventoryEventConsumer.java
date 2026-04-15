package com.microservices.notification.consumer;

import com.microservices.notification.domain.NotificationChannel;
import com.microservices.notification.domain.NotificationType;
import com.microservices.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "inventory-events", groupId = "notification-inventory-group",
            containerFactory = "kafkaListenerContainerFactory")
    @SuppressWarnings("unchecked")
    public void handleInventoryEvent(@Payload Object event,
                                     @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        log.info("Received inventory event with key: {}", key);

        if (event instanceof Map) {
            Map<String, Object> eventData = (Map<String, Object>) event;
            String eventType = (String) eventData.getOrDefault("eventType", "UNKNOWN");

            if ("LOW_STOCK".equals(eventType)) {
                handleLowStockEvent(eventData);
            } else {
                log.debug("Ignoring inventory event of type: {}", eventType);
            }
        } else {
            log.warn("Unexpected inventory event format: {}", event.getClass().getSimpleName());
        }
    }

    private void handleLowStockEvent(Map<String, Object> eventData) {
        String productId = (String) eventData.getOrDefault("productId", "unknown");
        Object currentStock = eventData.getOrDefault("currentStock", 0);
        Object threshold = eventData.getOrDefault("threshold", 0);

        log.warn("LOW STOCK ALERT: product={}, currentStock={}, threshold={}",
                productId, currentStock, threshold);

        notificationService.createAndSend(
                "SYSTEM-INVENTORY-" + productId,
                "SYSTEM",
                NotificationType.LOW_STOCK,
                NotificationChannel.EMAIL,
                "inventory-alerts@microservices-platform.com",
                "LOW STOCK ALERT - Product " + productId,
                String.format("⚠ Low Stock Alert\n\nProduct ID: %s\nCurrent Stock: %s\n" +
                        "Threshold: %s\n\nPlease replenish inventory immediately.",
                        productId, currentStock, threshold)
        );
    }
}
