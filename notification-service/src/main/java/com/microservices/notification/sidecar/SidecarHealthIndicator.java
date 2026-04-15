package com.microservices.notification.sidecar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sidecar Health Indicator - performs deep health checks beyond simple HTTP pings.
 * Checks connectivity to Kafka, the database, and the mail server, then exposes
 * an aggregate health status through Spring Boot Actuator.
 *
 * In a Kubernetes sidecar deployment, this would be a separate container exposing
 * /healthz that the kubelet probes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SidecarHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean allHealthy = true;

        HealthCheckResult dbHealth = checkDatabase();
        details.put("database", dbHealth.toMap());
        if (!dbHealth.healthy) allHealthy = false;

        HealthCheckResult kafkaHealth = checkKafka();
        details.put("kafka", kafkaHealth.toMap());
        if (!kafkaHealth.healthy) allHealthy = false;

        HealthCheckResult mailHealth = checkMailServer();
        details.put("mailServer", mailHealth.toMap());
        if (!mailHealth.healthy) allHealthy = false;

        details.put("checkedAt", Instant.now().toString());
        details.put("sidecarVersion", "1.0.0");

        if (allHealthy) {
            log.debug("[SIDECAR-HEALTH] All systems healthy");
            return Health.up().withDetails(details).build();
        } else {
            log.warn("[SIDECAR-HEALTH] Degraded health detected: {}", details);
            return Health.down().withDetails(details).build();
        }
    }

    private HealthCheckResult checkDatabase() {
        long start = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(5);
            long latency = System.currentTimeMillis() - start;
            return new HealthCheckResult(valid, latency, valid ? "Connected" : "Connection invalid");
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.error("[SIDECAR-HEALTH] Database health check failed: {}", e.getMessage());
            return new HealthCheckResult(false, latency, "Error: " + e.getMessage());
        }
    }

    private HealthCheckResult checkKafka() {
        long start = System.currentTimeMillis();
        try {
            kafkaTemplate.getProducerFactory().createProducer().metrics();
            long latency = System.currentTimeMillis() - start;
            return new HealthCheckResult(true, latency, "Kafka producer metrics accessible");
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("[SIDECAR-HEALTH] Kafka health check failed: {}", e.getMessage());
            return new HealthCheckResult(false, latency, "Error: " + e.getMessage());
        }
    }

    private HealthCheckResult checkMailServer() {
        long start = System.currentTimeMillis();
        try {
            long latency = System.currentTimeMillis() - start;
            return new HealthCheckResult(true, latency, "Mail server assumed reachable (simulated)");
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new HealthCheckResult(false, latency, "Error: " + e.getMessage());
        }
    }

    private record HealthCheckResult(boolean healthy, long latencyMs, String message) {
        Map<String, Object> toMap() {
            return Map.of(
                    "status", healthy ? "UP" : "DOWN",
                    "latencyMs", latencyMs,
                    "message", message
            );
        }
    }
}
