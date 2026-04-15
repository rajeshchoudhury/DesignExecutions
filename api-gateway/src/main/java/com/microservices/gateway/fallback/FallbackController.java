package com.microservices.gateway.fallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);
    private static final int RETRY_AFTER_SECONDS = 30;

    @GetMapping("/orders")
    public Mono<Map<String, Object>> ordersFallback(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        log.warn("[{}] Order service fallback triggered", correlationId);
        return Mono.just(buildFallbackResponse("Order service is temporarily unavailable"));
    }

    @GetMapping("/payments")
    public Mono<Map<String, Object>> paymentsFallback(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        log.warn("[{}] Payment service fallback triggered", correlationId);
        return Mono.just(buildFallbackResponse("Payment service is temporarily unavailable"));
    }

    @GetMapping("/inventory")
    public Mono<Map<String, Object>> inventoryFallback(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        log.warn("[{}] Inventory service fallback triggered", correlationId);
        return Mono.just(buildFallbackResponse("Inventory service is temporarily unavailable"));
    }

    private Map<String, Object> buildFallbackResponse(String message) {
        return Map.of(
                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "message", message,
                "timestamp", Instant.now().toString(),
                "retryAfterSeconds", RETRY_AFTER_SECONDS
        );
    }
}
