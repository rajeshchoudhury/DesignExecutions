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
public class OrderServiceClient {

    private static final String SERVICE_NAME = "order-service";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;

    public OrderServiceClient(WebClient.Builder loadBalancedWebClientBuilder) {
        this.webClient = loadBalancedWebClientBuilder
                .baseUrl("http://" + SERVICE_NAME)
                .build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getOrder(String orderId) {
        log.debug("Fetching order {} from {}", orderId, SERVICE_NAME);
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/api/orders/{orderId}", orderId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response != null && response.containsKey("data")) {
                return (Map<String, Object>) response.get("data");
            }
            return response;
        } catch (WebClientResponseException e) {
            log.error("Failed to fetch order {}: {} {}", orderId, e.getStatusCode(), e.getMessage());
            throw new RuntimeException("Order service returned " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Error calling order-service for order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to reach order-service: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getOrdersByCustomer(String customerId, int page, int size) {
        log.debug("Fetching orders for customer {} from {} (page={}, size={})",
                customerId, SERVICE_NAME, page, size);
        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/orders")
                            .queryParam("customerId", customerId)
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response != null && response.containsKey("data")) {
                Object data = response.get("data");
                if (data instanceof Map) {
                    Object content = ((Map<String, Object>) data).get("content");
                    if (content instanceof List) {
                        return (List<Map<String, Object>>) content;
                    }
                }
                if (data instanceof List) {
                    return (List<Map<String, Object>>) data;
                }
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching orders for customer {}: {}", customerId, e.getMessage());
            throw new RuntimeException("Failed to fetch customer orders: " + e.getMessage(), e);
        }
    }
}
