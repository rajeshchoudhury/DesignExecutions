package com.microservices.payment.command;

public class RefundPaymentCommand {

    private String paymentId;
    private String orderId;
    private String reason;

    public RefundPaymentCommand() {
    }

    public RefundPaymentCommand(String paymentId, String orderId, String reason) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.reason = reason;
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
}
