package com.microservices.payment.service;

import com.microservices.payment.dto.PaymentGatewayResponse;
import com.microservices.payment.dto.PaymentRequest;
import com.microservices.payment.dto.RefundResponse;

import java.math.BigDecimal;

public interface PaymentGatewayService {

    PaymentGatewayResponse processPayment(PaymentRequest request);

    RefundResponse refundPayment(String transactionId, BigDecimal amount);

    String getTransactionStatus(String transactionId);
}
