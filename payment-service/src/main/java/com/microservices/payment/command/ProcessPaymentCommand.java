package com.microservices.payment.command;

import com.microservices.payment.domain.PaymentMethod;
import java.math.BigDecimal;

public class ProcessPaymentCommand {

    private String orderId;
    private String customerId;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;

    public ProcessPaymentCommand() {
    }

    public ProcessPaymentCommand(String orderId, String customerId, BigDecimal amount, PaymentMethod paymentMethod) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
