package com.microservices.payment.service;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ResilienceMetricsService {

    private static final Logger log = LoggerFactory.getLogger(ResilienceMetricsService.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final RetryRegistry retryRegistry;

    public ResilienceMetricsService(CircuitBreakerRegistry circuitBreakerRegistry,
                                     BulkheadRegistry bulkheadRegistry,
                                     RetryRegistry retryRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        this.retryRegistry = retryRegistry;
    }

    public Map<String, Object> getCircuitBreakerState(String name) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();

        Map<String, Object> state = new HashMap<>();
        state.put("name", name);
        state.put("state", circuitBreaker.getState().name());
        state.put("failureRate", metrics.getFailureRate());
        state.put("slowCallRate", metrics.getSlowCallRate());
        state.put("bufferedCalls", metrics.getNumberOfBufferedCalls());
        state.put("failedCalls", metrics.getNumberOfFailedCalls());
        state.put("successfulCalls", metrics.getNumberOfSuccessfulCalls());
        state.put("notPermittedCalls", metrics.getNumberOfNotPermittedCalls());
        state.put("slowCalls", metrics.getNumberOfSlowCalls());
        state.put("slowSuccessfulCalls", metrics.getNumberOfSlowSuccessfulCalls());
        state.put("slowFailedCalls", metrics.getNumberOfSlowFailedCalls());

        log.debug("Circuit breaker '{}' state: {}", name, state);
        return state;
    }

    public Map<String, Object> getBulkheadMetrics(String name) {
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(name);
        Bulkhead.Metrics metrics = bulkhead.getMetrics();

        Map<String, Object> state = new HashMap<>();
        state.put("name", name);
        state.put("availableConcurrentCalls", metrics.getAvailableConcurrentCalls());
        state.put("maxAllowedConcurrentCalls", metrics.getMaxAllowedConcurrentCalls());

        log.debug("Bulkhead '{}' metrics: {}", name, state);
        return state;
    }

    public Map<String, Object> getRetryMetrics(String name) {
        Retry retry = retryRegistry.retry(name);
        Retry.Metrics metrics = retry.getMetrics();

        Map<String, Object> state = new HashMap<>();
        state.put("name", name);
        state.put("successfulCallsWithoutRetry", metrics.getNumberOfSuccessfulCallsWithoutRetryAttempt());
        state.put("successfulCallsWithRetry", metrics.getNumberOfSuccessfulCallsWithRetryAttempt());
        state.put("failedCallsWithRetry", metrics.getNumberOfFailedCallsWithRetryAttempt());
        state.put("failedCallsWithoutRetry", metrics.getNumberOfFailedCallsWithoutRetryAttempt());

        log.debug("Retry '{}' metrics: {}", name, state);
        return state;
    }

    public Map<String, Object> getAllMetrics(String name) {
        Map<String, Object> allMetrics = new HashMap<>();
        allMetrics.put("circuitBreaker", getCircuitBreakerState(name));
        allMetrics.put("bulkhead", getBulkheadMetrics(name));
        allMetrics.put("retry", getRetryMetrics(name));
        return allMetrics;
    }
}
