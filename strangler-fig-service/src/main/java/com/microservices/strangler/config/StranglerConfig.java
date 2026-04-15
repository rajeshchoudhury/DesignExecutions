package com.microservices.strangler.config;

import com.microservices.strangler.facade.RoutingStrategy;
import com.microservices.strangler.facade.StranglerFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;

/**
 * Configures initial feature flags and routing strategies for the Strangler Fig
 * migration. In production, these would come from an external configuration
 * service (Consul, LaunchDarkly, etc.).
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class StranglerConfig {

    private final StranglerFacade stranglerFacade;

    @Value("${strangler.endpoints.createOrder.strategy:CANARY}")
    private String createOrderStrategy;

    @Value("${strangler.endpoints.createOrder.modern-percent:30}")
    private int createOrderModernPercent;

    @Value("${strangler.endpoints.getOrder.strategy:MODERN_ONLY}")
    private String getOrderStrategy;

    @Value("${strangler.endpoints.updateOrder.strategy:CANARY}")
    private String updateOrderStrategy;

    @Value("${strangler.endpoints.updateOrder.modern-percent:20}")
    private int updateOrderModernPercent;

    @Value("${strangler.endpoints.getAllOrders.strategy:SHADOW}")
    private String getAllOrdersStrategy;

    @Value("${strangler.endpoints.deleteOrder.strategy:LEGACY_ONLY}")
    private String deleteOrderStrategy;

    @EventListener(ApplicationReadyEvent.class)
    public void applyInitialConfig() {
        log.info("========================================");
        log.info("  STRANGLER FIG SERVICE INITIALIZED");
        log.info("  Applying initial routing configuration");
        log.info("========================================");

        applyStrategy("createOrder", createOrderStrategy, createOrderModernPercent);
        applyStrategy("getOrder", getOrderStrategy, 100);
        applyStrategy("updateOrder", updateOrderStrategy, updateOrderModernPercent);
        applyStrategy("getAllOrders", getAllOrdersStrategy, 50);
        applyStrategy("deleteOrder", deleteOrderStrategy, 0);

        Map<String, Object> status = stranglerFacade.getMigrationStatus();
        log.info("Initial migration status: {}", status);
    }

    private void applyStrategy(String endpoint, String strategyName, int modernPercent) {
        try {
            RoutingStrategy strategy = RoutingStrategy.valueOf(strategyName);
            stranglerFacade.setRoutingStrategy(endpoint, strategy);
            stranglerFacade.setModernTrafficPercentage(endpoint, modernPercent);
            log.info("  {} -> {} ({}% modern)", endpoint, strategy, modernPercent);
        } catch (IllegalArgumentException e) {
            log.error("Invalid strategy '{}' for endpoint '{}', defaulting to LEGACY_ONLY",
                    strategyName, endpoint);
            stranglerFacade.setRoutingStrategy(endpoint, RoutingStrategy.LEGACY_ONLY);
        }
    }
}
