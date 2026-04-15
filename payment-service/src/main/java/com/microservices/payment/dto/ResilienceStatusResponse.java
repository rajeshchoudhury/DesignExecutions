package com.microservices.payment.dto;

import java.util.Map;

public class ResilienceStatusResponse {

    private String circuitBreakerState;
    private float failureRate;
    private int bulkheadAvailableCalls;
    private Map<String, Object> retryMetrics;

    public ResilienceStatusResponse() {
    }

    public ResilienceStatusResponse(String circuitBreakerState, float failureRate,
                                     int bulkheadAvailableCalls, Map<String, Object> retryMetrics) {
        this.circuitBreakerState = circuitBreakerState;
        this.failureRate = failureRate;
        this.bulkheadAvailableCalls = bulkheadAvailableCalls;
        this.retryMetrics = retryMetrics;
    }

    public String getCircuitBreakerState() {
        return circuitBreakerState;
    }

    public void setCircuitBreakerState(String circuitBreakerState) {
        this.circuitBreakerState = circuitBreakerState;
    }

    public float getFailureRate() {
        return failureRate;
    }

    public void setFailureRate(float failureRate) {
        this.failureRate = failureRate;
    }

    public int getBulkheadAvailableCalls() {
        return bulkheadAvailableCalls;
    }

    public void setBulkheadAvailableCalls(int bulkheadAvailableCalls) {
        this.bulkheadAvailableCalls = bulkheadAvailableCalls;
    }

    public Map<String, Object> getRetryMetrics() {
        return retryMetrics;
    }

    public void setRetryMetrics(Map<String, Object> retryMetrics) {
        this.retryMetrics = retryMetrics;
    }
}
