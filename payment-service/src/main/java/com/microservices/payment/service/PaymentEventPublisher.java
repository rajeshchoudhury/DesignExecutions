package com.microservices.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.payment.event.PaymentCompletedEvent;
import com.microservices.payment.event.PaymentFailedEvent;
import com.microservices.payment.event.PaymentRefundedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);
    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PaymentEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        publishEvent(event.getPaymentId(), event, "PaymentCompleted");
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        publishEvent(event.getPaymentId(), event, "PaymentFailed");
    }

    public void publishPaymentRefunded(PaymentRefundedEvent event) {
        publishEvent(event.getPaymentId(), event, "PaymentRefunded");
    }

    private void publishEvent(String key, Object event, String eventType) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            log.info("Publishing {} event: key={}", eventType, key);

            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, key, payload);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish {} event: key={}", eventType, key, ex);
                } else {
                    log.info("Successfully published {} event: key={}, partition={}, offset={}",
                            eventType, key,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize {} event: key={}", eventType, key, e);
        }
    }
}
