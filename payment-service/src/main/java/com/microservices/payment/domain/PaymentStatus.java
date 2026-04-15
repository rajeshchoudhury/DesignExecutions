package com.microservices.payment.domain;

public enum PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REFUNDED,
    REFUND_PENDING
}
