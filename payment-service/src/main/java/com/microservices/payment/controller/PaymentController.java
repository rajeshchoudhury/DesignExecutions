package com.microservices.payment.controller;

import com.microservices.payment.command.ProcessPaymentCommand;
import com.microservices.payment.command.RefundPaymentCommand;
import com.microservices.payment.dto.PaymentRequest;
import com.microservices.payment.dto.PaymentResponse;
import com.microservices.payment.dto.ResilienceStatusResponse;
import com.microservices.payment.service.PaymentService;
import com.microservices.payment.service.ResilienceMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payment API", description = "Payment processing with Circuit Breaker, Bulkhead, and Retry patterns")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final ResilienceMetricsService resilienceMetricsService;

    public PaymentController(PaymentService paymentService,
                             ResilienceMetricsService resilienceMetricsService) {
        this.paymentService = paymentService;
        this.resilienceMetricsService = resilienceMetricsService;
    }

    @PostMapping("/process")
    @Operation(summary = "Process a payment",
            description = "Processes a payment through the external gateway with circuit breaker, " +
                    "bulkhead, and retry protection")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment processed",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid payment request"),
            @ApiResponse(responseCode = "503", description = "Payment gateway unavailable (circuit breaker open)")
    })
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody PaymentRequest request) {
        log.info("Received payment request for orderId={}", request.getOrderId());

        ProcessPaymentCommand command = new ProcessPaymentCommand(
                request.getOrderId(), request.getCustomerId(),
                request.getAmount(), request.getPaymentMethod());

        PaymentResponse response = paymentService.processPayment(command);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{paymentId}/refund")
    @Operation(summary = "Refund a payment",
            description = "Initiates a refund for a completed payment with circuit breaker and retry protection")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund processed",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found"),
            @ApiResponse(responseCode = "400", description = "Payment cannot be refunded")
    })
    public ResponseEntity<PaymentResponse> refundPayment(
            @Parameter(description = "Payment ID to refund") @PathVariable String paymentId,
            @RequestParam(required = false, defaultValue = "Customer requested refund") String reason) {
        log.info("Received refund request for paymentId={}", paymentId);

        RefundPaymentCommand command = new RefundPaymentCommand(paymentId, null, reason);
        PaymentResponse response = paymentService.refundPayment(command);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment details",
            description = "Retrieves payment details by payment ID with circuit breaker protection for gateway status check")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment details retrieved",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    public ResponseEntity<PaymentResponse> getPaymentDetails(
            @Parameter(description = "Payment ID") @PathVariable String paymentId) {
        log.info("Fetching payment details for paymentId={}", paymentId);
        PaymentResponse response = paymentService.getPaymentStatus(paymentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get payments for an order",
            description = "Retrieves all payment records associated with an order")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payments retrieved")
    })
    public ResponseEntity<List<PaymentResponse>> getPaymentsByOrder(
            @Parameter(description = "Order ID") @PathVariable String orderId) {
        log.info("Fetching payments for orderId={}", orderId);
        List<PaymentResponse> responses = paymentService.getPaymentsByOrderId(orderId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/resilience/status")
    @Operation(summary = "Get resilience patterns status",
            description = "Returns current state of circuit breakers, bulkheads, and retry metrics")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resilience status retrieved",
                    content = @Content(schema = @Schema(implementation = ResilienceStatusResponse.class)))
    })
    public ResponseEntity<ResilienceStatusResponse> getResilienceStatus() {
        log.info("Fetching resilience patterns status");

        Map<String, Object> cbState = resilienceMetricsService.getCircuitBreakerState("paymentGateway");
        Map<String, Object> bulkheadMetrics = resilienceMetricsService.getBulkheadMetrics("paymentGateway");
        Map<String, Object> retryMetrics = resilienceMetricsService.getRetryMetrics("paymentGateway");

        ResilienceStatusResponse response = new ResilienceStatusResponse();
        response.setCircuitBreakerState((String) cbState.get("state"));
        response.setFailureRate((float) cbState.get("failureRate"));
        response.setBulkheadAvailableCalls((int) bulkheadMetrics.get("availableConcurrentCalls"));
        response.setRetryMetrics(retryMetrics);

        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleBadState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }
}
