package com.microservices.composition.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class NotificationServiceClient {

    private static final String SERVICE_NAME = "notification-service";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;

    public NotificationServiceClient(WebClient.Builder loadBalancedWebClientBuilder) {
        this.webClient = loadBalancedWebClientBuilder
                .baseUrl("http://" + SERVICE_NAME)
                .build();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getNotificationsByOrder(String orderId) {
        log.debug("Fetching notifications for order {} from {}", orderId, SERVICE_NAME);
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/api/notifications/order/{orderId}", orderId)
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
            log.error("Error fetching notifications for order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to reach notification-service: " + e.getMessage(), e);
        }
    }
}
