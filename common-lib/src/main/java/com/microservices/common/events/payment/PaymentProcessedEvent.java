package com.microservices.common.events.payment;

import lombok.Value;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Value
public class PaymentProcessedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    String paymentId;
    String orderId;
    BigDecimal amount;
    PaymentStatus status;
    Instant processedAt;
}
