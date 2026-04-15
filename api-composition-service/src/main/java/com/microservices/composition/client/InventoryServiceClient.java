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
public class InventoryServiceClient {

    private static final String SERVICE_NAME = "inventory-service";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;

    public InventoryServiceClient(WebClient.Builder loadBalancedWebClientBuilder) {
        this.webClient = loadBalancedWebClientBuilder
                .baseUrl("http://" + SERVICE_NAME)
                .build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getProductAvailability(String productId) {
        log.debug("Fetching product availability for {} from {}", productId, SERVICE_NAME);
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/api/inventory/products/{productId}/availability", productId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response != null && response.containsKey("data")) {
                return (Map<String, Object>) response.get("data");
            }
            return response;
        } catch (WebClientResponseException e) {
            log.error("Failed to fetch availability for product {}: {} {}",
                    productId, e.getStatusCode(), e.getMessage());
            throw new RuntimeException("Inventory service returned " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Error calling inventory-service for product {}: {}", productId, e.getMessage());
            throw new RuntimeException("Failed to reach inventory-service: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getStockMovements(String productId) {
        log.debug("Fetching stock movements for {} from {}", productId, SERVICE_NAME);
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/api/inventory/products/{productId}/movements", productId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response != null && response.containsKey("data")) {
                Object data = response.get("data");
                if (data instanceof List) {
                    return (List<Map<String, Object>>) data;
                }
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching stock movements for product {}: {}", productId, e.getMessage());
            throw new RuntimeException("Failed to fetch stock movements: " + e.getMessage(), e);
        }
    }
}
