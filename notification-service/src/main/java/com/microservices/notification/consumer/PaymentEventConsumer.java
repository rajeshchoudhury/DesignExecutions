package com.microservices.notification.consumer;

import com.microservices.common.events.payment.PaymentFailedEvent;
import com.microservices.common.events.payment.PaymentProcessedEvent;
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
public class PaymentEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "payment-events", groupId = "notification-payment-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handlePaymentEvent(@Payload Object event,
                                   @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        log.info("Received payment event with key: {}", key);

        if (event instanceof PaymentProcessedEvent paymentEvent) {
            handlePaymentProcessed(paymentEvent);
        } else if (event instanceof PaymentFailedEvent paymentEvent) {
            handlePaymentFailed(paymentEvent);
        } else {
            log.warn("Unknown payment event type: {}", event.getClass().getSimpleName());
        }
    }

    private void handlePaymentProcessed(PaymentProcessedEvent event) {
        log.info("Processing PaymentProcessedEvent for order: {}, payment: {}",
                event.getOrderId(), event.getPaymentId());
        notificationService.createAndSend(
                event.getOrderId(),
                null,
                NotificationType.PAYMENT_PROCESSED,
                NotificationChannel.EMAIL,
                "order-" + event.getOrderId() + "@customer.example.com",
                "Payment Confirmed - Order " + event.getOrderId(),
                String.format("Your payment of $%s for order %s has been successfully processed.\n" +
                                "Payment ID: %s\nProcessed at: %s",
                        event.getAmount(), event.getOrderId(),
                        event.getPaymentId(), event.getProcessedAt())
        );
    }

    private void handlePaymentFailed(PaymentFailedEvent event) {
        log.info("Processing PaymentFailedEvent for order: {}, payment: {}",
                event.getOrderId(), event.getPaymentId());
        notificationService.createAndSend(
                event.getOrderId(),
                null,
                NotificationType.PAYMENT_FAILED,
                NotificationChannel.EMAIL,
                "order-" + event.getOrderId() + "@customer.example.com",
                "Payment Failed - Order " + event.getOrderId(),
                String.format("Payment for order %s has failed.\nPayment ID: %s\nReason: %s\n\n" +
                                "Please update your payment method and try again.",
                        event.getOrderId(), event.getPaymentId(), event.getReason())
        );
    }
}
