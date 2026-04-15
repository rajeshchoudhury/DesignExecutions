package com.microservices.payment.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentCompletedEvent {

    private String paymentId;
    private String orderId;
    private BigDecimal amount;
    private String gatewayTransactionId;
    private LocalDateTime processedAt;

    public PaymentCompletedEvent() {
    }

    public PaymentCompletedEvent(String paymentId, String orderId, BigDecimal amount,
                                  String gatewayTransactionId, LocalDateTime processedAt) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
        this.gatewayTransactionId = gatewayTransactionId;
        this.processedAt = processedAt;
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

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public void setGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
