package com.microservices.payment.dto;

import java.math.BigDecimal;

public class RefundResponse {

    private String refundId;
    private String status;
    private BigDecimal amount;

    public RefundResponse() {
    }

    public RefundResponse(String refundId, String status, BigDecimal amount) {
        this.refundId = refundId;
        this.status = status;
        this.amount = amount;
    }

    public String getRefundId() {
        return refundId;
    }

    public void setRefundId(String refundId) {
        this.refundId = refundId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public boolean isSuccessful() {
        return "SUCCESS".equalsIgnoreCase(status);
    }
}
