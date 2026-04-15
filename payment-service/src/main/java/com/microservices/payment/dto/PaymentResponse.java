package com.microservices.payment.dto;

import com.microservices.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentResponse {

    private String paymentId;
    private String orderId;
    private PaymentStatus status;
    private BigDecimal amount;
    private LocalDateTime processedAt;
    private String gatewayTransactionId;

    public PaymentResponse() {
    }

    public PaymentResponse(String paymentId, String orderId, PaymentStatus status,
                           BigDecimal amount, LocalDateTime processedAt, String gatewayTransactionId) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.status = status;
        this.amount = amount;
        this.processedAt = processedAt;
        this.gatewayTransactionId = gatewayTransactionId;
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

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public void setGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
    }
}
