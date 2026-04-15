package com.microservices.notification.sidecar;

import com.microservices.notification.domain.NotificationChannel;
import com.microservices.notification.domain.NotificationType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sidecar Config Provider - periodically polls an external configuration source
 * (simulated here) and provides dynamic configuration to the main service.
 *
 * This demonstrates how sidecars decouple cross-cutting concerns from business logic.
 * The main notification service doesn't need to know about config polling intervals,
 * config formats, or config source credentials. The sidecar handles all of this and
 * exposes a clean API.
 *
 * In production, this would poll Consul, etcd, Spring Cloud Config, or AWS Parameter Store.
 */
@Slf4j
@Component
public class SidecarConfigProvider {

    private final Map<NotificationChannel, Boolean> channelEnabled = new ConcurrentHashMap<>();
    private final Map<NotificationChannel, Integer> channelRateLimits = new ConcurrentHashMap<>();
    private final Map<NotificationType, RetryPolicy> retryPolicies = new ConcurrentHashMap<>();

    @Getter
    private volatile Instant lastRefreshTime = Instant.now();

    private final AtomicInteger refreshCount = new AtomicInteger(0);

    public SidecarConfigProvider() {
        initializeDefaults();
    }

    private void initializeDefaults() {
        for (NotificationChannel channel : NotificationChannel.values()) {
            channelEnabled.put(channel, true);
        }

        channelRateLimits.put(NotificationChannel.EMAIL, 100);
        channelRateLimits.put(NotificationChannel.SMS, 50);
        channelRateLimits.put(NotificationChannel.PUSH, 200);
        channelRateLimits.put(NotificationChannel.WEBHOOK, 500);

        for (NotificationType type : NotificationType.values()) {
            retryPolicies.put(type, new RetryPolicy(3, 5000, 2.0));
        }
        retryPolicies.put(NotificationType.PAYMENT_FAILED, new RetryPolicy(5, 2000, 1.5));
        retryPolicies.put(NotificationType.LOW_STOCK, new RetryPolicy(1, 0, 1.0));
    }

    /**
     * Polls external config source every 60 seconds. In production, this would
     * make HTTP calls to a config server. Here we simulate dynamic config changes.
     */
    @Scheduled(fixedDelayString = "${sidecar.config.poll-interval-ms:60000}")
    public void pollExternalConfig() {
        int count = refreshCount.incrementAndGet();
        log.info("[SIDECAR-CONFIG] Polling external configuration source... (refresh #{})", count);

        try {
            simulateExternalConfigFetch(count);
            lastRefreshTime = Instant.now();
            log.info("[SIDECAR-CONFIG] Configuration refreshed successfully at {}", lastRefreshTime);
        } catch (Exception e) {
            log.error("[SIDECAR-CONFIG] Failed to refresh config: {}", e.getMessage());
        }
    }

    private void simulateExternalConfigFetch(int refreshNumber) {
        if (refreshNumber % 10 == 0) {
            channelRateLimits.put(NotificationChannel.EMAIL,
                    100 + (refreshNumber % 5) * 10);
            log.info("[SIDECAR-CONFIG] Updated EMAIL rate limit to {}",
                    channelRateLimits.get(NotificationChannel.EMAIL));
        }

        if (refreshNumber % 20 == 0) {
            log.info("[SIDECAR-CONFIG] Simulating SMS channel maintenance window");
        }
    }

    public boolean isChannelEnabled(NotificationChannel channel) {
        return channelEnabled.getOrDefault(channel, false);
    }

    public void setChannelEnabled(NotificationChannel channel, boolean enabled) {
        channelEnabled.put(channel, enabled);
        log.info("[SIDECAR-CONFIG] Channel {} set to {}", channel, enabled ? "ENABLED" : "DISABLED");
    }

    public int getRateLimit(NotificationChannel channel) {
        return channelRateLimits.getOrDefault(channel, 100);
    }

    public RetryPolicy getRetryPolicy(NotificationType type) {
        return retryPolicies.getOrDefault(type, new RetryPolicy(3, 5000, 2.0));
    }

    public int getRefreshCount() {
        return refreshCount.get();
    }

    public Map<String, Object> getFullConfig() {
        return Map.of(
                "channelEnabled", Map.copyOf(channelEnabled),
                "channelRateLimits", Map.copyOf(channelRateLimits),
                "retryPolicies", Map.copyOf(retryPolicies),
                "lastRefreshTime", lastRefreshTime.toString(),
                "refreshCount", refreshCount.get()
        );
    }

    public record RetryPolicy(int maxAttempts, long initialDelayMs, double backoffMultiplier) {}
}
