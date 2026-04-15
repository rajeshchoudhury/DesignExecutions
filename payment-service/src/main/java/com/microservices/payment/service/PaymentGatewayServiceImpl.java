package com.microservices.payment.service;

import com.microservices.payment.dto.PaymentGatewayResponse;
import com.microservices.payment.dto.PaymentRequest;
import com.microservices.payment.dto.RefundResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;

/**
 * Simulated external payment gateway that deliberately introduces random failures
 * to demonstrate resilience patterns (Circuit Breaker, Retry, Bulkhead).
 */
@Service
public class PaymentGatewayServiceImpl implements PaymentGatewayService {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayServiceImpl.class);

    @Value("${payment.gateway.success-rate:70}")
    private int successRate;

    @Value("${payment.gateway.min-delay-ms:100}")
    private int minDelayMs;

    @Value("${payment.gateway.max-delay-ms:500}")
    private int maxDelayMs;

    @Value("${payment.gateway.timeout-delay-ms:5000}")
    private int timeoutDelayMs;

    @Override
    public PaymentGatewayResponse processPayment(PaymentRequest request) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[Gateway-{}] Processing payment for order={}, amount={}, method={}",
                requestId, request.getOrderId(), request.getAmount(), request.getPaymentMethod());

        simulateLatency();

        int outcome = ThreadLocalRandom.current().nextInt(100);

        if (outcome < successRate) {
            String transactionId = "TXN-" + UUID.randomUUID().toString();
            log.info("[Gateway-{}] Payment successful. transactionId={}", requestId, transactionId);
            return new PaymentGatewayResponse(transactionId, "SUCCESS", "Payment processed successfully");
        }

        if (outcome < successRate + 20) {
            log.warn("[Gateway-{}] Payment gateway timeout - simulating slow response", requestId);
            try {
                Thread.sleep(timeoutDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Gateway timeout interrupted", e);
            }
            throw new RuntimeException(new TimeoutException(
                    "Payment gateway timed out after " + timeoutDelayMs + "ms"));
        }

        log.error("[Gateway-{}] Payment gateway error - simulating exception", requestId);
        throw new RuntimeException(new IOException("Payment gateway connection refused"));
    }

    @Override
    public RefundResponse refundPayment(String transactionId, BigDecimal amount) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[Gateway-{}] Processing refund for transactionId={}, amount={}",
                requestId, transactionId, amount);

        simulateLatency();

        int outcome = ThreadLocalRandom.current().nextInt(100);

        if (outcome < 80) {
            String refundId = "REF-" + UUID.randomUUID().toString();
            log.info("[Gateway-{}] Refund successful. refundId={}", requestId, refundId);
            return new RefundResponse(refundId, "SUCCESS", amount);
        }

        log.error("[Gateway-{}] Refund failed at gateway", requestId);
        throw new RuntimeException(new IOException("Refund gateway error"));
    }

    @Override
    public String getTransactionStatus(String transactionId) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[Gateway-{}] Fetching status for transactionId={}", requestId, transactionId);

        simulateLatency();

        int outcome = ThreadLocalRandom.current().nextInt(100);

        if (outcome < 90) {
            log.info("[Gateway-{}] Status retrieved successfully", requestId);
            return "COMPLETED";
        }

        log.error("[Gateway-{}] Status check failed", requestId);
        throw new RuntimeException(new IOException("Gateway status check failed"));
    }

    private void simulateLatency() {
        try {
            int delay = ThreadLocalRandom.current().nextInt(minDelayMs, maxDelayMs + 1);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
