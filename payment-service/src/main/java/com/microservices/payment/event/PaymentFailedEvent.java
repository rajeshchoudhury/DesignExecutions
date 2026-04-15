package com.microservices.payment.event;

import java.time.LocalDateTime;

public class PaymentFailedEvent {

    private String paymentId;
    private String orderId;
    private String reason;
    private LocalDateTime failedAt;

    public PaymentFailedEvent() {
    }

    public PaymentFailedEvent(String paymentId, String orderId, String reason, LocalDateTime failedAt) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.reason = reason;
        this.failedAt = failedAt;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(LocalDateTime failedAt) {
        this.failedAt = failedAt;
    }
}
