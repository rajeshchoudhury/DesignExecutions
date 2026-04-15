package com.microservices.payment.service;

import com.microservices.payment.command.ProcessPaymentCommand;
import com.microservices.payment.command.RefundPaymentCommand;
import com.microservices.payment.domain.Payment;
import com.microservices.payment.domain.PaymentStatus;
import com.microservices.payment.dto.PaymentGatewayResponse;
import com.microservices.payment.dto.PaymentRequest;
import com.microservices.payment.dto.PaymentResponse;
import com.microservices.payment.dto.RefundResponse;
import com.microservices.payment.event.PaymentCompletedEvent;
import com.microservices.payment.event.PaymentFailedEvent;
import com.microservices.payment.event.PaymentRefundedEvent;
import com.microservices.payment.repository.PaymentRepository;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayService paymentGatewayService;
    private final PaymentEventPublisher paymentEventPublisher;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentGatewayService paymentGatewayService,
                          PaymentEventPublisher paymentEventPublisher) {
        this.paymentRepository = paymentRepository;
        this.paymentGatewayService = paymentGatewayService;
        this.paymentEventPublisher = paymentEventPublisher;
    }

    // ─── Process Payment ─────────────────────────────────────────────
    // Resilience4j decorates in order: Retry -> CircuitBreaker -> Bulkhead -> TimeLimiter

    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "processPaymentFallback")
    @Bulkhead(name = "paymentGateway", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "processPaymentBulkheadFallback")
    @Retry(name = "paymentGateway", fallbackMethod = "processPaymentRetryFallback")
    @TimeLimiter(name = "paymentGateway")
    @Transactional
    public PaymentResponse processPayment(ProcessPaymentCommand command) {
        log.info("Processing payment for orderId={}, customerId={}, amount={}",
                command.getOrderId(), command.getCustomerId(), command.getAmount());

        if (paymentRepository.existsByOrderIdAndStatus(command.getOrderId(), PaymentStatus.COMPLETED)) {
            log.warn("Payment already completed for orderId={}", command.getOrderId());
            Payment existing = paymentRepository.findByOrderId(command.getOrderId()).stream()
                    .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
                    .findFirst()
                    .orElseThrow();
            return toPaymentResponse(existing);
        }

        Payment payment = new Payment();
        payment.setPaymentId(UUID.randomUUID().toString());
        payment.setOrderId(command.getOrderId());
        payment.setCustomerId(command.getCustomerId());
        payment.setAmount(command.getAmount());
        payment.setPaymentMethod(command.getPaymentMethod());
        payment.setStatus(PaymentStatus.PROCESSING);
        payment = paymentRepository.save(payment);

        try {
            PaymentRequest gatewayRequest = new PaymentRequest(
                    command.getOrderId(), command.getCustomerId(),
                    command.getAmount(), command.getPaymentMethod());

            PaymentGatewayResponse gatewayResponse = paymentGatewayService.processPayment(gatewayRequest);

            if (gatewayResponse.isSuccessful()) {
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setGatewayTransactionId(gatewayResponse.getTransactionId());
                payment.setProcessedAt(LocalDateTime.now());
                payment = paymentRepository.save(payment);

                publishPaymentCompletedEvent(payment);
                log.info("Payment completed: paymentId={}, txnId={}",
                        payment.getPaymentId(), gatewayResponse.getTransactionId());
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(gatewayResponse.getMessage());
                payment = paymentRepository.save(payment);

                publishPaymentFailedEvent(payment, gatewayResponse.getMessage());
                log.warn("Payment failed at gateway: paymentId={}, reason={}",
                        payment.getPaymentId(), gatewayResponse.getMessage());
            }
        } catch (Exception e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            paymentRepository.save(payment);

            publishPaymentFailedEvent(payment, e.getMessage());
            throw e;
        }

        return toPaymentResponse(payment);
    }

    // ─── Circuit Breaker Fallbacks ───────────────────────────────────

    private PaymentResponse processPaymentFallback(ProcessPaymentCommand command, CallNotPermittedException ex) {
        log.error("CIRCUIT BREAKER OPEN: Payment gateway is unavailable. " +
                        "orderId={}, Circuit breaker is not permitting calls.",
                command.getOrderId());
        return buildFallbackResponse(command, "Circuit breaker is OPEN - payment gateway unavailable");
    }

    private PaymentResponse processPaymentFallback(ProcessPaymentCommand command, Exception ex) {
        log.error("CIRCUIT BREAKER FALLBACK: Payment processing failed. orderId={}, error={}",
                command.getOrderId(), ex.getMessage());
        return buildFallbackResponse(command, "Payment processing failed: " + ex.getMessage());
    }

    // ─── Bulkhead Fallback ───────────────────────────────────────────

    private PaymentResponse processPaymentBulkheadFallback(ProcessPaymentCommand command, BulkheadFullException ex) {
        log.error("BULKHEAD FULL: Too many concurrent payment requests. orderId={}, " +
                        "Thread pool/semaphore exhausted.",
                command.getOrderId());
        return buildFallbackResponse(command,
                "System is overloaded - too many concurrent payment requests. Please retry later.");
    }

    // ─── Retry Fallback ──────────────────────────────────────────────

    private PaymentResponse processPaymentRetryFallback(ProcessPaymentCommand command, Exception ex) {
        log.error("RETRY EXHAUSTED: All retry attempts failed for orderId={}. " +
                        "Last error: {}",
                command.getOrderId(), ex.getMessage());
        return buildFallbackResponse(command,
                "All retry attempts exhausted. Last error: " + ex.getMessage());
    }

    // ─── Refund Payment ──────────────────────────────────────────────

    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "refundPaymentFallback")
    @Retry(name = "paymentGateway", fallbackMethod = "refundPaymentRetryFallback")
    @Transactional
    public PaymentResponse refundPayment(RefundPaymentCommand command) {
        log.info("Processing refund for paymentId={}, orderId={}, reason={}",
                command.getPaymentId(), command.getOrderId(), command.getReason());

        Payment payment = paymentRepository.findByPaymentId(command.getPaymentId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment not found: " + command.getPaymentId()));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Cannot refund payment in status: " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.REFUND_PENDING);
        paymentRepository.save(payment);

        RefundResponse refundResponse = paymentGatewayService.refundPayment(
                payment.getGatewayTransactionId(), payment.getAmount());

        if (refundResponse.isSuccessful()) {
            payment.setStatus(PaymentStatus.REFUNDED);
            payment = paymentRepository.save(payment);

            publishPaymentRefundedEvent(payment);
            log.info("Refund completed: paymentId={}, refundId={}",
                    payment.getPaymentId(), refundResponse.getRefundId());
        } else {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setFailureReason("Refund failed: " + refundResponse.getStatus());
            payment = paymentRepository.save(payment);
            log.warn("Refund failed: paymentId={}", payment.getPaymentId());
        }

        return toPaymentResponse(payment);
    }

    private PaymentResponse refundPaymentFallback(RefundPaymentCommand command, Exception ex) {
        log.error("CIRCUIT BREAKER FALLBACK: Refund processing failed. paymentId={}, error={}",
                command.getPaymentId(), ex.getMessage());
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(command.getPaymentId());
        response.setOrderId(command.getOrderId());
        response.setStatus(PaymentStatus.REFUND_PENDING);
        return response;
    }

    private PaymentResponse refundPaymentRetryFallback(RefundPaymentCommand command, Exception ex) {
        log.error("RETRY EXHAUSTED: All refund retry attempts failed. paymentId={}, error={}",
                command.getPaymentId(), ex.getMessage());
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(command.getPaymentId());
        response.setOrderId(command.getOrderId());
        response.setStatus(PaymentStatus.REFUND_PENDING);
        return response;
    }

    // ─── Get Payment Status ──────────────────────────────────────────

    @CircuitBreaker(name = "paymentGatewayRead", fallbackMethod = "getPaymentStatusFallback")
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentStatus(String paymentId) {
        log.info("Fetching payment status for paymentId={}", paymentId);

        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment not found: " + paymentId));

        if (payment.getGatewayTransactionId() != null
                && payment.getStatus() == PaymentStatus.PROCESSING) {
            String gatewayStatus = paymentGatewayService.getTransactionStatus(
                    payment.getGatewayTransactionId());
            log.info("Gateway status for paymentId={}: {}", paymentId, gatewayStatus);
        }

        return toPaymentResponse(payment);
    }

    private PaymentResponse getPaymentStatusFallback(String paymentId, Exception ex) {
        log.warn("CIRCUIT BREAKER (read): Returning cached payment status for paymentId={}. error={}",
                paymentId, ex.getMessage());
        return paymentRepository.findByPaymentId(paymentId)
                .map(this::toPaymentResponse)
                .orElseGet(() -> {
                    PaymentResponse response = new PaymentResponse();
                    response.setPaymentId(paymentId);
                    response.setStatus(PaymentStatus.PENDING);
                    return response;
                });
    }

    // ─── Query Methods ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByOrderId(String orderId) {
        return paymentRepository.findByOrderId(orderId).stream()
                .map(this::toPaymentResponse)
                .collect(Collectors.toList());
    }

    // ─── Event Publishing ────────────────────────────────────────────

    private void publishPaymentCompletedEvent(Payment payment) {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                payment.getPaymentId(), payment.getOrderId(),
                payment.getAmount(), payment.getGatewayTransactionId(),
                payment.getProcessedAt());
        paymentEventPublisher.publishPaymentCompleted(event);
    }

    private void publishPaymentFailedEvent(Payment payment, String reason) {
        PaymentFailedEvent event = new PaymentFailedEvent(
                payment.getPaymentId(), payment.getOrderId(),
                reason, LocalDateTime.now());
        paymentEventPublisher.publishPaymentFailed(event);
    }

    private void publishPaymentRefundedEvent(Payment payment) {
        PaymentRefundedEvent event = new PaymentRefundedEvent(
                payment.getPaymentId(), payment.getOrderId(),
                payment.getAmount(), LocalDateTime.now());
        paymentEventPublisher.publishPaymentRefunded(event);
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentId(), payment.getOrderId(),
                payment.getStatus(), payment.getAmount(),
                payment.getProcessedAt(), payment.getGatewayTransactionId());
    }

    private PaymentResponse buildFallbackResponse(ProcessPaymentCommand command, String reason) {
        Payment failedPayment = new Payment();
        failedPayment.setPaymentId(UUID.randomUUID().toString());
        failedPayment.setOrderId(command.getOrderId());
        failedPayment.setCustomerId(command.getCustomerId());
        failedPayment.setAmount(command.getAmount());
        failedPayment.setPaymentMethod(command.getPaymentMethod());
        failedPayment.setStatus(PaymentStatus.FAILED);
        failedPayment.setFailureReason(reason);

        try {
            paymentRepository.save(failedPayment);
        } catch (Exception e) {
            log.error("Failed to persist fallback payment record: {}", e.getMessage());
        }

        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(failedPayment.getPaymentId());
        response.setOrderId(command.getOrderId());
        response.setStatus(PaymentStatus.FAILED);
        response.setAmount(command.getAmount());
        return response;
    }
}
