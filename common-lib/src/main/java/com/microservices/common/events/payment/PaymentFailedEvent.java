package com.microservices.common.events.payment;

import lombok.Value;

import java.io.Serializable;
import java.time.Instant;

@Value
public class PaymentFailedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    String paymentId;
    String orderId;
    String reason;
    Instant failedAt;
}
