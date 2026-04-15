package com.microservices.payment.dto;

public class PaymentGatewayResponse {

    private String transactionId;
    private String status;
    private String message;

    public PaymentGatewayResponse() {
    }

    public PaymentGatewayResponse(String transactionId, String status, String message) {
        this.transactionId = transactionId;
        this.status = status;
        this.message = message;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSuccessful() {
        return "SUCCESS".equalsIgnoreCase(status);
    }
}
