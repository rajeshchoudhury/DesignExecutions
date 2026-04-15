package com.microservices.composition.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PaymentServiceClient {

    private static final String SERVICE_NAME = "payment-service";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;

    public PaymentServiceClient(WebClient.Builder loadBalancedWebClientBuilder) {
        this.webClient = loadBalancedWebClientBuilder
                .baseUrl("http://" + SERVICE_NAME)
                .build();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getPaymentsByOrder(String orderId) {
        log.debug("Fetching payments for order {} from {}", orderId, SERVICE_NAME);
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/api/payments/order/{orderId}", orderId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response != null && response.containsKey("data")) {
                Object data = response.get("data");
                if (data instanceof List) {
                    return (List<Map<String, Object>>) data;
                }
                return List.of((Map<String, Object>) data);
            }
            return Collections.emptyList();
        } catch (WebClientResponseException e) {
            log.error("Failed to fetch payments for order {}: {} {}",
                    orderId, e.getStatusCode(), e.getMessage());
            throw new RuntimeException("Payment service returned " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Error calling payment-service for order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to reach payment-service: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getPaymentStatus(String paymentId) {
        log.debug("Fetching payment status for {} from {}", paymentId, SERVICE_NAME);
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/api/payments/{paymentId}", paymentId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response != null && response.containsKey("data")) {
                return (Map<String, Object>) response.get("data");
            }
            return response;
        } catch (Exception e) {
            log.error("Error fetching payment status for {}: {}", paymentId, e.getMessage());
            throw new RuntimeException("Failed to fetch payment status: " + e.getMessage(), e);
        }
    }
}
