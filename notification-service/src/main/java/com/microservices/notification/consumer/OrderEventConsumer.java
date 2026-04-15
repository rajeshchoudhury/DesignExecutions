package com.microservices.notification.consumer;

import com.microservices.common.events.order.*;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "order-events", groupId = "notification-order-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderEvent(@Payload Object event,
                                 @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        log.info("Received order event with key: {}", key);

        if (event instanceof OrderCreatedEvent orderEvent) {
            handleOrderCreated(orderEvent);
        } else if (event instanceof OrderApprovedEvent orderEvent) {
            handleOrderApproved(orderEvent);
        } else if (event instanceof OrderRejectedEvent orderEvent) {
            handleOrderRejected(orderEvent);
        } else if (event instanceof OrderCompletedEvent orderEvent) {
            handleOrderCompleted(orderEvent);
        } else {
            log.warn("Unknown order event type: {}", event.getClass().getSimpleName());
        }
    }

    private void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Processing OrderCreatedEvent for order: {}", event.getOrderId());
        notificationService.createAndSend(
                event.getOrderId(),
                event.getCustomerId(),
                NotificationType.ORDER_CREATED,
                NotificationChannel.EMAIL,
                event.getCustomerId() + "@customer.example.com",
                "Order Confirmation - " + event.getOrderId(),
                buildOrderCreatedBody(event)
        );
    }

    private void handleOrderApproved(OrderApprovedEvent event) {
        log.info("Processing OrderApprovedEvent for order: {}", event.getOrderId());
        notificationService.createAndSend(
                event.getOrderId(),
                null,
                NotificationType.ORDER_APPROVED,
                NotificationChannel.EMAIL,
                "order-" + event.getOrderId() + "@customer.example.com",
                "Order Approved - " + event.getOrderId(),
                String.format("Your order %s has been approved and is being processed. Approved at: %s",
                        event.getOrderId(), event.getApprovedAt())
        );
    }

    private void handleOrderRejected(OrderRejectedEvent event) {
        log.info("Processing OrderRejectedEvent for order: {}", event.getOrderId());
        notificationService.createAndSend(
                event.getOrderId(),
                null,
                NotificationType.ORDER_REJECTED,
                NotificationChannel.EMAIL,
                "order-" + event.getOrderId() + "@customer.example.com",
                "Order Rejected - " + event.getOrderId(),
                String.format("Your order %s has been rejected. Reason: %s",
                        event.getOrderId(), event.getReason())
        );
    }

    private void handleOrderCompleted(OrderCompletedEvent event) {
        log.info("Processing OrderCompletedEvent for order: {}", event.getOrderId());
        notificationService.createAndSend(
                event.getOrderId(),
                null,
                NotificationType.ORDER_COMPLETED,
                NotificationChannel.EMAIL,
                "order-" + event.getOrderId() + "@customer.example.com",
                "Order Completed - " + event.getOrderId(),
                String.format("Your order %s has been completed successfully! Completed at: %s",
                        event.getOrderId(), event.getCompletedAt())
        );
    }

    private String buildOrderCreatedBody(OrderCreatedEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("Thank you for your order!\n\n");
        sb.append("Order ID: ").append(event.getOrderId()).append("\n");
        sb.append("Total Amount: $").append(event.getTotalAmount()).append("\n\n");
        sb.append("Items:\n");
        event.getItems().forEach(item ->
                sb.append("  - ").append(item.getProductName())
                        .append(" x").append(item.getQuantity())
                        .append(" @ $").append(item.getUnitPrice())
                        .append("\n")
        );
        sb.append("\nWe'll notify you when your order is approved.");
        return sb.toString();
    }
}
