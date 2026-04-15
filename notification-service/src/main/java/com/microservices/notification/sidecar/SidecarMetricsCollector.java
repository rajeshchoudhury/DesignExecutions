package com.microservices.notification.sidecar;

import com.microservices.notification.domain.NotificationChannel;
import com.microservices.notification.domain.NotificationStatus;
import com.microservices.notification.domain.NotificationType;
import com.microservices.notification.repository.NotificationRepository;
import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sidecar Metrics Collector - collects and exposes custom application metrics
 * in Prometheus format. In a production sidecar deployment, this would be a
 * StatsD/Prometheus exporter container running alongside the main service.
 */
@Slf4j
@Component
public class SidecarMetricsCollector {

    private final MeterRegistry meterRegistry;
    private final NotificationRepository notificationRepository;

    private final Map<String, Counter> sentCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> failedCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> latencyTimers = new ConcurrentHashMap<>();
    private final AtomicLong pendingQueueSize = new AtomicLong(0);

    private final Counter totalRequestsCounter;
    private final Counter successRequestsCounter;
    private final Counter failureRequestsCounter;

    public SidecarMetricsCollector(MeterRegistry meterRegistry,
                                   NotificationRepository notificationRepository) {
        this.meterRegistry = meterRegistry;
        this.notificationRepository = notificationRepository;

        this.totalRequestsCounter = Counter.builder("sidecar.requests.total")
                .description("Total requests proxied through sidecar")
                .register(meterRegistry);

        this.successRequestsCounter = Counter.builder("sidecar.requests.success")
                .description("Successful requests through sidecar")
                .register(meterRegistry);

        this.failureRequestsCounter = Counter.builder("sidecar.requests.failure")
                .description("Failed requests through sidecar")
                .register(meterRegistry);

        Gauge.builder("notifications.queue.size", pendingQueueSize, AtomicLong::get)
                .description("Current number of pending notifications in the queue")
                .register(meterRegistry);

        for (NotificationType type : NotificationType.values()) {
            for (NotificationChannel channel : NotificationChannel.values()) {
                String key = type.name() + "." + channel.name();
                sentCounters.put(key, Counter.builder("notifications.sent")
                        .tag("type", type.name())
                        .tag("channel", channel.name())
                        .description("Notifications sent by type and channel")
                        .register(meterRegistry));

                failedCounters.put(key, Counter.builder("notifications.failed")
                        .tag("type", type.name())
                        .tag("channel", channel.name())
                        .description("Notifications failed by type and channel")
                        .register(meterRegistry));

                latencyTimers.put(key, Timer.builder("notifications.latency")
                        .tag("type", type.name())
                        .tag("channel", channel.name())
                        .description("Notification send latency by type and channel")
                        .register(meterRegistry));
            }
        }
    }

    public void recordNotificationSent(NotificationType type, NotificationChannel channel,
                                       long latencyMs) {
        String key = type.name() + "." + channel.name();
        sentCounters.getOrDefault(key, totalRequestsCounter).increment();
        latencyTimers.get(key).record(latencyMs, TimeUnit.MILLISECONDS);
    }

    public void recordNotificationFailed(NotificationType type, NotificationChannel channel) {
        String key = type.name() + "." + channel.name();
        failedCounters.getOrDefault(key, failureRequestsCounter).increment();
    }

    public void recordRequestStart(String operation) {
        totalRequestsCounter.increment();
        Counter.builder("sidecar.operations.started")
                .tag("operation", operation)
                .register(meterRegistry)
                .increment();
    }

    public void recordRequestEnd(String operation, long latencyMs, boolean success) {
        if (success) {
            successRequestsCounter.increment();
        } else {
            failureRequestsCounter.increment();
        }

        Timer.builder("sidecar.operations.latency")
                .tag("operation", operation)
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .record(latencyMs, TimeUnit.MILLISECONDS);
    }

    public void recordMethodExecution(String method, long durationMs, boolean success) {
        Timer.builder("sidecar.method.execution")
                .tag("method", method)
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    @Scheduled(fixedRate = 15000)
    public void refreshQueueMetrics() {
        long pending = notificationRepository.findByStatus(NotificationStatus.PENDING).size();
        long retrying = notificationRepository.findByStatus(NotificationStatus.RETRYING).size();
        pendingQueueSize.set(pending + retrying);

        log.debug("[SIDECAR-METRICS] Queue size refresh: pending={}, retrying={}, total={}",
                pending, retrying, pending + retrying);
    }
}
