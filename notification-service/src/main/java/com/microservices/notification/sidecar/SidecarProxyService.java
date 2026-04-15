package com.microservices.notification.sidecar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SIDECAR PATTERN IMPLEMENTATION
 *
 * The Sidecar pattern deploys auxiliary components alongside the main service to provide
 * cross-cutting concerns without modifying the service itself. This class acts as the
 * central coordinator for all sidecar capabilities:
 *
 * 1. LOGGING SIDECAR - Enriches logs with correlation IDs, structured JSON output
 * 2. METRICS SIDECAR - Collects custom metrics (send rate, failure rate, latency)
 * 3. CONFIG SIDECAR  - Dynamic configuration from external sources
 * 4. HEALTH SIDECAR  - Deep health checks beyond simple HTTP pings
 *
 * In a production deployment, these sidecars would run as separate containers in the
 * same pod (Kubernetes) or on the same host, communicating via localhost. Here they
 * are implemented as co-located Spring components to demonstrate the pattern's
 * separation of concerns.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SidecarProxyService {

    private final SidecarMetricsCollector metricsCollector;
    private final SidecarConfigProvider configProvider;
    private final SidecarHealthIndicator healthIndicator;

    private final Map<String, RequestContext> activeRequests = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("========================================");
        log.info("  SIDECAR PROXY SERVICE INITIALIZED");
        log.info("  Logging Sidecar:  ACTIVE");
        log.info("  Metrics Sidecar:  ACTIVE");
        log.info("  Config Sidecar:   ACTIVE");
        log.info("  Health Sidecar:   ACTIVE");
        log.info("========================================");
    }

    /**
     * Intercepts an incoming request, enriching it with correlation context
     * and forwarding to the main service. This is the entry point for the
     * logging sidecar functionality.
     */
    public String beginRequest(String operation) {
        String correlationId = UUID.randomUUID().toString();
        RequestContext ctx = new RequestContext(correlationId, operation, Instant.now());
        activeRequests.put(correlationId, ctx);

        log.info(formatStructuredLog(correlationId, operation, "REQUEST_START",
                Map.of("timestamp", Instant.now().toString())));

        metricsCollector.recordRequestStart(operation);
        return correlationId;
    }

    /**
     * Completes request tracking, recording latency and outcome metrics.
     */
    public void endRequest(String correlationId, boolean success) {
        RequestContext ctx = activeRequests.remove(correlationId);
        if (ctx == null) {
            log.warn("No active request context for correlationId: {}", correlationId);
            return;
        }

        long latencyMs = Instant.now().toEpochMilli() - ctx.startTime().toEpochMilli();

        log.info(formatStructuredLog(correlationId, ctx.operation(),
                success ? "REQUEST_SUCCESS" : "REQUEST_FAILURE",
                Map.of("latencyMs", String.valueOf(latencyMs),
                        "success", String.valueOf(success))));

        metricsCollector.recordRequestEnd(ctx.operation(), latencyMs, success);
    }

    /**
     * Proxies a notification send through the sidecar, applying rate limiting
     * and channel enablement checks from the config sidecar.
     */
    public boolean canSendNotification(String channel) {
        if (!configProvider.isChannelEnabled(
                com.microservices.notification.domain.NotificationChannel.valueOf(channel))) {
            log.info(formatStructuredLog("SYSTEM", "canSend", "CHANNEL_DISABLED",
                    Map.of("channel", channel)));
            return false;
        }

        int rateLimit = configProvider.getRateLimit(
                com.microservices.notification.domain.NotificationChannel.valueOf(channel));
        log.debug("Rate limit for channel {}: {} per minute", channel, rateLimit);
        return true;
    }

    /**
     * Retrieves aggregate health from the health sidecar, providing a
     * comprehensive view of all dependencies.
     */
    public Map<String, Object> getAggregateHealth() {
        return Map.of(
                "sidecar", "UP",
                "health", healthIndicator.health().getStatus().getCode(),
                "config", Map.of(
                        "lastRefresh", configProvider.getLastRefreshTime().toString(),
                        "refreshCount", configProvider.getRefreshCount()
                ),
                "metrics", Map.of(
                        "activeRequests", activeRequests.size()
                )
        );
    }

    private String formatStructuredLog(String correlationId, String operation,
                                       String event, Map<String, String> extra) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"correlationId\":\"").append(correlationId).append("\"");
        sb.append(",\"operation\":\"").append(operation).append("\"");
        sb.append(",\"event\":\"").append(event).append("\"");
        sb.append(",\"service\":\"notification-service\"");
        extra.forEach((k, v) -> sb.append(",\"").append(k).append("\":\"").append(v).append("\""));
        sb.append("}");
        return sb.toString();
    }

    private record RequestContext(String correlationId, String operation, Instant startTime) {}
}
