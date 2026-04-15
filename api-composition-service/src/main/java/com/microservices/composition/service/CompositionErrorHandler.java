package com.microservices.composition.service;

import com.microservices.composition.dto.CompositionMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Handles partial failures in API composition. When composing data from multiple
 * downstream services, individual service failures should not prevent the entire
 * composition from returning. This handler tracks which services succeeded and
 * which failed, allowing the composition to return partial data with error context.
 */
@Slf4j
@Component
public class CompositionErrorHandler {

    /**
     * Wraps a downstream service call, capturing failures into the composition metadata
     * rather than propagating them. Returns null on failure, allowing the composition
     * to proceed with partial data.
     */
    public <T> T executeWithFallback(String serviceName, CompositionMetadata metadata,
                                     Supplier<T> serviceCall) {
        long start = System.currentTimeMillis();
        try {
            T result = serviceCall.get();
            long latency = System.currentTimeMillis() - start;
            metadata.recordServiceSuccess(serviceName, latency);
            log.debug("Service {} responded in {}ms", serviceName, latency);
            return result;
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            metadata.recordServiceFailure(serviceName, e.getMessage());
            log.warn("Service {} failed after {}ms: {}", serviceName, latency, e.getMessage());
            return null;
        }
    }

    /**
     * Creates a CompletableFuture for a downstream service call that captures
     * failures into metadata instead of propagating exceptions.
     */
    public <T> CompletableFuture<T> executeAsync(String serviceName, CompositionMetadata metadata,
                                                  Supplier<T> serviceCall) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            try {
                T result = serviceCall.get();
                long latency = System.currentTimeMillis() - start;
                metadata.recordServiceSuccess(serviceName, latency);
                log.debug("Async call to {} completed in {}ms", serviceName, latency);
                return result;
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - start;
                metadata.recordServiceFailure(serviceName, e.getMessage());
                log.warn("Async call to {} failed after {}ms: {}", serviceName, latency, e.getMessage());
                return null;
            }
        });
    }

    /**
     * Builds a summary log line for the entire composition result.
     */
    public void logCompositionResult(String operation, CompositionMetadata metadata) {
        if (metadata.isFullyResolved()) {
            log.info("Composition '{}' fully resolved: {} services responded in {}ms",
                    operation, metadata.getSuccessCount(), metadata.getTotalLatencyMs());
        } else {
            log.warn("Composition '{}' partially resolved: {}/{} services responded, " +
                            "failures: {} | totalLatency={}ms",
                    operation, metadata.getSuccessCount(),
                    metadata.getSuccessCount() + metadata.getFailureCount(),
                    metadata.getFailedServices(), metadata.getTotalLatencyMs());
        }
    }
}
