package com.microservices.strangler.facade;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks migration metrics to monitor the progress and health of the
 * Strangler Fig migration. Exposes data via Actuator custom endpoint.
 */
@Slf4j
@Component
public class MigrationMetrics {

    private final MeterRegistry meterRegistry;

    @Getter private final AtomicLong totalRequests = new AtomicLong(0);
    @Getter private final AtomicLong legacyRequests = new AtomicLong(0);
    @Getter private final AtomicLong modernRequests = new AtomicLong(0);
    @Getter private final AtomicLong shadowModeDiscrepancies = new AtomicLong(0);

    private final Map<String, AtomicLong> legacyLatencies = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> modernLatencies = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> legacyCallCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> modernCallCounts = new ConcurrentHashMap<>();

    private final Counter legacyCounter;
    private final Counter modernCounter;
    private final Counter discrepancyCounter;
    private final Timer legacyTimer;
    private final Timer modernTimer;

    public MigrationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.legacyCounter = Counter.builder("strangler.requests.legacy")
                .description("Requests routed to legacy implementation")
                .register(meterRegistry);

        this.modernCounter = Counter.builder("strangler.requests.modern")
                .description("Requests routed to modern implementation")
                .register(meterRegistry);

        this.discrepancyCounter = Counter.builder("strangler.shadow.discrepancies")
                .description("Response discrepancies found in shadow mode")
                .register(meterRegistry);

        this.legacyTimer = Timer.builder("strangler.latency.legacy")
                .description("Legacy implementation latency")
                .register(meterRegistry);

        this.modernTimer = Timer.builder("strangler.latency.modern")
                .description("Modern implementation latency")
                .register(meterRegistry);
    }

    public void recordLegacyRequest(String endpoint, long latencyMs) {
        totalRequests.incrementAndGet();
        legacyRequests.incrementAndGet();
        legacyCounter.increment();
        legacyTimer.record(latencyMs, TimeUnit.MILLISECONDS);

        legacyLatencies.computeIfAbsent(endpoint, k -> new AtomicLong(0)).addAndGet(latencyMs);
        legacyCallCounts.computeIfAbsent(endpoint, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void recordModernRequest(String endpoint, long latencyMs) {
        totalRequests.incrementAndGet();
        modernRequests.incrementAndGet();
        modernCounter.increment();
        modernTimer.record(latencyMs, TimeUnit.MILLISECONDS);

        modernLatencies.computeIfAbsent(endpoint, k -> new AtomicLong(0)).addAndGet(latencyMs);
        modernCallCounts.computeIfAbsent(endpoint, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void recordShadowDiscrepancy(String endpoint) {
        shadowModeDiscrepancies.incrementAndGet();
        discrepancyCounter.increment();
        log.warn("[MIGRATION-METRICS] Shadow mode discrepancy on endpoint: {}", endpoint);
    }

    public double getAverageLegacyLatencyMs() {
        long totalLatency = legacyLatencies.values().stream().mapToLong(AtomicLong::get).sum();
        long totalCalls = legacyCallCounts.values().stream().mapToLong(AtomicLong::get).sum();
        return totalCalls > 0 ? (double) totalLatency / totalCalls : 0.0;
    }

    public double getAverageModernLatencyMs() {
        long totalLatency = modernLatencies.values().stream().mapToLong(AtomicLong::get).sum();
        long totalCalls = modernCallCounts.values().stream().mapToLong(AtomicLong::get).sum();
        return totalCalls > 0 ? (double) totalLatency / totalCalls : 0.0;
    }

    public Map<String, Object> getMetricsSummary() {
        Map<String, Object> summary = new ConcurrentHashMap<>();
        summary.put("totalRequests", totalRequests.get());
        summary.put("legacyRequests", legacyRequests.get());
        summary.put("modernRequests", modernRequests.get());
        summary.put("shadowModeDiscrepancies", shadowModeDiscrepancies.get());
        summary.put("averageLegacyLatencyMs", String.format("%.2f", getAverageLegacyLatencyMs()));
        summary.put("averageModernLatencyMs", String.format("%.2f", getAverageModernLatencyMs()));

        double legacyPct = totalRequests.get() > 0
                ? (double) legacyRequests.get() / totalRequests.get() * 100 : 0;
        double modernPct = totalRequests.get() > 0
                ? (double) modernRequests.get() / totalRequests.get() * 100 : 0;
        summary.put("legacyTrafficPercent", String.format("%.1f%%", legacyPct));
        summary.put("modernTrafficPercent", String.format("%.1f%%", modernPct));

        return summary;
    }
}
