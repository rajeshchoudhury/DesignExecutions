package com.microservices.composition.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompositionMetadata {

    @Builder.Default
    private Set<String> respondedServices = new HashSet<>();

    @Builder.Default
    private Map<String, String> failedServices = new HashMap<>();

    private long totalLatencyMs;

    @Builder.Default
    private Map<String, Long> perServiceLatencyMs = new HashMap<>();

    public void recordServiceSuccess(String serviceName, long latencyMs) {
        respondedServices.add(serviceName);
        perServiceLatencyMs.put(serviceName, latencyMs);
    }

    public void recordServiceFailure(String serviceName, String errorMessage) {
        failedServices.put(serviceName, errorMessage);
    }

    public boolean isFullyResolved() {
        return failedServices.isEmpty();
    }

    public int getSuccessCount() {
        return respondedServices.size();
    }

    public int getFailureCount() {
        return failedServices.size();
    }
}
