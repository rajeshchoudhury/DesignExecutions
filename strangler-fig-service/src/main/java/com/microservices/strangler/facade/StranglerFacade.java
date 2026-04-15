package com.microservices.strangler.facade;

import com.microservices.strangler.legacy.LegacyOrderService;
import com.microservices.strangler.modern.service.ModernOrderService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * STRANGLER FIG PATTERN IMPLEMENTATION
 *
 * This facade acts as the routing layer that gradually migrates traffic from the
 * legacy monolith to the modern microservice implementation. Named after the
 * strangler fig tree that slowly envelops its host tree, this pattern allows
 * incremental migration without a risky big-bang cutover.
 *
 * Key capabilities:
 *
 * 1. FEATURE FLAGS: Per-endpoint routing strategy configuration
 * 2. CANARY ROUTING: Percentage-based traffic splitting between legacy and modern
 * 3. SHADOW MODE: Dual execution with response comparison for validation
 * 4. GRADUAL MIGRATION: Automatic traffic percentage increase on schedule
 * 5. METRICS: Full visibility into which path is handling traffic and performance
 *
 * Migration lifecycle per endpoint:
 *   LEGACY_ONLY -> SHADOW -> CANARY -> GRADUAL_MIGRATION -> MODERN_ONLY
 */
@Slf4j
@Component
public class StranglerFacade {

    private final LegacyOrderService legacyService;
    private final ModernOrderService modernService;
    private final MigrationMetrics metrics;

    @Getter
    private final Map<String, RoutingStrategy> routingStrategies = new ConcurrentHashMap<>();

    @Getter
    private final Map<String, Integer> modernTrafficPercentage = new ConcurrentHashMap<>();

    private static final int GRADUAL_INCREMENT = 5;
    private static final int MAX_PERCENTAGE = 100;

    public StranglerFacade(LegacyOrderService legacyService,
                           ModernOrderService modernService,
                           MigrationMetrics metrics,
                           @org.springframework.beans.factory.annotation.Value("${strangler.default-strategy:CANARY}")
                           String defaultStrategy) {
        this.legacyService = legacyService;
        this.modernService = modernService;
        this.metrics = metrics;

        RoutingStrategy strategy = RoutingStrategy.valueOf(defaultStrategy);
        routingStrategies.put("createOrder", strategy);
        routingStrategies.put("getOrder", RoutingStrategy.MODERN_ONLY);
        routingStrategies.put("updateOrder", strategy);
        routingStrategies.put("deleteOrder", RoutingStrategy.LEGACY_ONLY);
        routingStrategies.put("getAllOrders", RoutingStrategy.SHADOW);

        modernTrafficPercentage.put("createOrder", 30);
        modernTrafficPercentage.put("getOrder", 100);
        modernTrafficPercentage.put("updateOrder", 20);
        modernTrafficPercentage.put("deleteOrder", 0);
        modernTrafficPercentage.put("getAllOrders", 50);
    }

    /**
     * Routes a request to the appropriate implementation based on the endpoint's
     * routing strategy and traffic percentage configuration.
     *
     * @return the result of the routed operation, along with metadata about which path was used
     */
    @SuppressWarnings("unchecked")
    public RoutingResult routeRequest(String endpoint, Map<String, Object> request) {
        RoutingStrategy strategy = routingStrategies.getOrDefault(endpoint, RoutingStrategy.LEGACY_ONLY);
        log.info("[STRANGLER] Routing '{}' with strategy={}, modernPct={}%",
                endpoint, strategy, modernTrafficPercentage.getOrDefault(endpoint, 0));

        return switch (strategy) {
            case LEGACY_ONLY -> executeLegacy(endpoint, request);
            case MODERN_ONLY -> executeModern(endpoint, request);
            case CANARY -> executeCanary(endpoint, request);
            case SHADOW -> executeShadow(endpoint, request);
            case GRADUAL_MIGRATION -> executeGradualMigration(endpoint, request);
        };
    }

    private RoutingResult executeLegacy(String endpoint, Map<String, Object> request) {
        long start = System.currentTimeMillis();
        Object result = dispatchToLegacy(endpoint, request);
        long latency = System.currentTimeMillis() - start;
        metrics.recordLegacyRequest(endpoint, latency);

        return new RoutingResult(result, "LEGACY", latency, null);
    }

    private RoutingResult executeModern(String endpoint, Map<String, Object> request) {
        long start = System.currentTimeMillis();
        Object result = dispatchToModern(endpoint, request);
        long latency = System.currentTimeMillis() - start;
        metrics.recordModernRequest(endpoint, latency);

        return new RoutingResult(result, "MODERN", latency, null);
    }

    private RoutingResult executeCanary(String endpoint, Map<String, Object> request) {
        int threshold = modernTrafficPercentage.getOrDefault(endpoint, 0);
        boolean useModern = ThreadLocalRandom.current().nextInt(100) < threshold;

        if (useModern) {
            log.info("[STRANGLER-CANARY] Routing to MODERN (threshold: {}%)", threshold);
            return executeModern(endpoint, request);
        } else {
            log.info("[STRANGLER-CANARY] Routing to LEGACY (threshold: {}%)", threshold);
            return executeLegacy(endpoint, request);
        }
    }

    /**
     * SHADOW MODE: Sends request to BOTH implementations, returns the legacy response,
     * but compares the two responses for verification. Any discrepancies are logged
     * and tracked in metrics.
     */
    private RoutingResult executeShadow(String endpoint, Map<String, Object> request) {
        long legacyStart = System.currentTimeMillis();
        Object legacyResult = dispatchToLegacy(endpoint, request);
        long legacyLatency = System.currentTimeMillis() - legacyStart;
        metrics.recordLegacyRequest(endpoint, legacyLatency);

        Object modernResult = null;
        long modernLatency = 0;
        String comparisonResult;
        try {
            long modernStart = System.currentTimeMillis();
            modernResult = dispatchToModern(endpoint, request);
            modernLatency = System.currentTimeMillis() - modernStart;
            metrics.recordModernRequest(endpoint, modernLatency);

            comparisonResult = compareResponses(endpoint, legacyResult, modernResult);
        } catch (Exception e) {
            comparisonResult = "MODERN_FAILED: " + e.getMessage();
            log.error("[STRANGLER-SHADOW] Modern execution failed for {}: {}", endpoint, e.getMessage());
            metrics.recordShadowDiscrepancy(endpoint);
        }

        log.info("[STRANGLER-SHADOW] {} | legacyLatency={}ms, modernLatency={}ms | comparison={}",
                endpoint, legacyLatency, modernLatency, comparisonResult);

        return new RoutingResult(legacyResult, "SHADOW(LEGACY_RETURNED)", legacyLatency, comparisonResult);
    }

    private RoutingResult executeGradualMigration(String endpoint, Map<String, Object> request) {
        return executeCanary(endpoint, request);
    }

    /**
     * Compares legacy and modern responses to detect behavioral differences.
     * In production, this would use deep comparison and field-level diffing.
     */
    @SuppressWarnings("unchecked")
    public String compareResponses(String endpoint, Object legacyResponse, Object modernResponse) {
        if (legacyResponse == null && modernResponse == null) return "MATCH(both_null)";
        if (legacyResponse == null || modernResponse == null) {
            metrics.recordShadowDiscrepancy(endpoint);
            return "MISMATCH(null_vs_non_null)";
        }

        if (legacyResponse instanceof Map && modernResponse instanceof Map) {
            Map<String, Object> legacy = (Map<String, Object>) legacyResponse;
            Map<String, Object> modern = (Map<String, Object>) modernResponse;

            Set<String> ignoredFields = Set.of("source", "orderId", "createdAt", "updatedAt");
            List<String> diffs = new ArrayList<>();

            for (String key : legacy.keySet()) {
                if (ignoredFields.contains(key)) continue;
                Object legacyVal = legacy.get(key);
                Object modernVal = modern.get(key);
                if (!Objects.equals(String.valueOf(legacyVal), String.valueOf(modernVal))) {
                    diffs.add(key + ": legacy=" + legacyVal + " vs modern=" + modernVal);
                }
            }

            if (diffs.isEmpty()) {
                return "MATCH(structural_equivalent)";
            } else {
                metrics.recordShadowDiscrepancy(endpoint);
                log.warn("[STRANGLER-SHADOW] Discrepancies for {}: {}", endpoint, diffs);
                return "MISMATCH(" + diffs.size() + " diffs: " + String.join("; ", diffs) + ")";
            }
        }

        if (legacyResponse instanceof List && modernResponse instanceof List) {
            int legacySize = ((List<?>) legacyResponse).size();
            int modernSize = ((List<?>) modernResponse).size();
            if (legacySize == modernSize) {
                return "MATCH(list_size=" + legacySize + ")";
            } else {
                metrics.recordShadowDiscrepancy(endpoint);
                return "MISMATCH(list_size: legacy=" + legacySize + " vs modern=" + modernSize + ")";
            }
        }

        return "INCONCLUSIVE(different_types)";
    }

    /**
     * Gradually increases modern traffic percentage for endpoints using
     * GRADUAL_MIGRATION strategy. Runs every 5 minutes.
     */
    @Scheduled(fixedDelayString = "${strangler.gradual-migration-interval-ms:300000}")
    public void graduallyIncreasModernTraffic() {
        routingStrategies.forEach((endpoint, strategy) -> {
            if (strategy == RoutingStrategy.GRADUAL_MIGRATION) {
                int current = modernTrafficPercentage.getOrDefault(endpoint, 0);
                if (current < MAX_PERCENTAGE) {
                    int newPct = Math.min(current + GRADUAL_INCREMENT, MAX_PERCENTAGE);
                    modernTrafficPercentage.put(endpoint, newPct);
                    log.info("[STRANGLER-GRADUAL] Increased modern traffic for '{}': {}% -> {}%",
                            endpoint, current, newPct);

                    if (newPct >= MAX_PERCENTAGE) {
                        routingStrategies.put(endpoint, RoutingStrategy.MODERN_ONLY);
                        log.info("[STRANGLER-GRADUAL] Migration complete for '{}' - switched to MODERN_ONLY",
                                endpoint);
                    }
                }
            }
        });
    }

    public void setRoutingStrategy(String endpoint, RoutingStrategy strategy) {
        routingStrategies.put(endpoint, strategy);
        log.info("[STRANGLER] Updated routing for '{}': {}", endpoint, strategy);
    }

    public void setModernTrafficPercentage(String endpoint, int percentage) {
        modernTrafficPercentage.put(endpoint, Math.max(0, Math.min(100, percentage)));
        log.info("[STRANGLER] Updated modern traffic for '{}': {}%", endpoint, percentage);
    }

    public Map<String, Object> getMigrationStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("endpoints", new LinkedHashMap<>());
        routingStrategies.forEach((endpoint, strategy) -> {
            Map<String, Object> endpointStatus = new LinkedHashMap<>();
            endpointStatus.put("strategy", strategy.name());
            endpointStatus.put("modernTrafficPercent", modernTrafficPercentage.getOrDefault(endpoint, 0));
            ((Map<String, Object>) status.get("endpoints")).put(endpoint, endpointStatus);
        });
        status.put("metrics", metrics.getMetricsSummary());
        return status;
    }

    private Object dispatchToLegacy(String endpoint, Map<String, Object> request) {
        return switch (endpoint) {
            case "createOrder" -> legacyService.createOrder(request);
            case "getOrder" -> legacyService.getOrder((String) request.get("orderId"));
            case "updateOrder" -> legacyService.updateOrder(
                    (String) request.get("orderId"), request);
            case "getAllOrders" -> legacyService.getAllOrders();
            case "deleteOrder" -> {
                legacyService.deleteOrder((String) request.get("orderId"));
                yield Map.of("deleted", true, "source", "LEGACY");
            }
            default -> throw new IllegalArgumentException("Unknown endpoint: " + endpoint);
        };
    }

    private Object dispatchToModern(String endpoint, Map<String, Object> request) {
        return switch (endpoint) {
            case "createOrder" -> modernService.createOrder(request);
            case "getOrder" -> modernService.getOrder((String) request.get("orderId"));
            case "updateOrder" -> modernService.updateOrder(
                    (String) request.get("orderId"), request);
            case "getAllOrders" -> modernService.getAllOrders();
            case "deleteOrder" -> {
                modernService.deleteOrder((String) request.get("orderId"));
                yield Map.of("deleted", true, "source", "MODERN");
            }
            default -> throw new IllegalArgumentException("Unknown endpoint: " + endpoint);
        };
    }

    public record RoutingResult(Object data, String routedTo, long latencyMs, String shadowComparison) {}
}
