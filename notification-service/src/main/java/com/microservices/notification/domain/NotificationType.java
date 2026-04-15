package com.microservices.notification.domain;

public enum NotificationType {
    ORDER_CREATED,
    ORDER_APPROVED,
    ORDER_REJECTED,
    ORDER_COMPLETED,
    PAYMENT_PROCESSED,
    PAYMENT_FAILED,
    LOW_STOCK
}
