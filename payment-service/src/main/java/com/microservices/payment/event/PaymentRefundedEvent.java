package com.microservices.payment.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentRefundedEvent {

    private String paymentId;
    private String orderId;
    private BigDecimal amount;
    private LocalDateTime refundedAt;

    public PaymentRefundedEvent() {
    }

    public PaymentRefundedEvent(String paymentId, String orderId, BigDecimal amount, LocalDateTime refundedAt) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
        this.refundedAt = refundedAt;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }

    public void setRefundedAt(LocalDateTime refundedAt) {
        this.refundedAt = refundedAt;
    }
}
