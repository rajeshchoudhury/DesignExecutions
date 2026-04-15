package com.microservices.payment.config;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.github.resilience4j.common.bulkhead.configuration.ThreadPoolBulkheadConfigurationProperties;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Configuration
public class ResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

    /**
     * Programmatic CircuitBreaker configuration as backup to YAML.
     * YAML config takes precedence when present; these serve as documented defaults.
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig paymentGatewayConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(5)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .slowCallRateThreshold(80)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(IOException.class, TimeoutException.class, RuntimeException.class)
                .build();

        CircuitBreakerConfig paymentGatewayReadConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(70)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slowCallDurationThreshold(Duration.ofSeconds(3))
                .slowCallRateThreshold(90)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(paymentGatewayConfig);

        registry.circuitBreaker("paymentGateway", paymentGatewayConfig);
        registry.circuitBreaker("paymentGatewayRead", paymentGatewayReadConfig);

        return registry;
    }

    @Bean
    public BulkheadRegistry bulkheadRegistry() {
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(10)
                .maxWaitDuration(Duration.ofMillis(500))
                .build();

        BulkheadRegistry registry = BulkheadRegistry.of(bulkheadConfig);
        registry.bulkhead("paymentGateway", bulkheadConfig);

        return registry;
    }

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(1))
                .enableExponentialBackoff()
                .exponentialBackoffMultiplier(2)
                .retryExceptions(IOException.class, TimeoutException.class, RuntimeException.class)
                .build();

        RetryRegistry registry = RetryRegistry.of(retryConfig);
        registry.retry("paymentGateway", retryConfig);

        return registry;
    }

    @PostConstruct
    public void registerCircuitBreakerEventListeners() {
        CircuitBreakerRegistry registry = circuitBreakerRegistry();

        registry.getAllCircuitBreakers().forEach(cb -> {
            cb.getEventPublisher()
                    .onStateTransition(this::onStateTransition)
                    .onFailureRateExceeded(event ->
                            log.warn("Circuit breaker '{}' failure rate exceeded: {}%",
                                    event.getCircuitBreakerName(),
                                    event.getEventType()))
                    .onSlowCallRateExceeded(event ->
                            log.warn("Circuit breaker '{}' slow call rate exceeded",
                                    event.getCircuitBreakerName()))
                    .onCallNotPermitted(event ->
                            log.error("Circuit breaker '{}' rejected call - state is OPEN",
                                    event.getCircuitBreakerName()));
        });
    }

    private void onStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        log.warn("=== CIRCUIT BREAKER STATE TRANSITION === " +
                        "'{}': {} → {}",
                event.getCircuitBreakerName(),
                event.getStateTransition().getFromState(),
                event.getStateTransition().getToState());
    }
}
