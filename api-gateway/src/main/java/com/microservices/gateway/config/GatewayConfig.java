package com.microservices.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

@Configuration
public class GatewayConfig {

    private final RedisRateLimiter redisRateLimiter;
    private final KeyResolver userKeyResolver;

    public GatewayConfig(RedisRateLimiter redisRateLimiter, KeyResolver userKeyResolver) {
        this.redisRateLimiter = redisRateLimiter;
        this.userKeyResolver = userKeyResolver;
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("order-service", r -> r
                        .path("/api/orders/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb
                                        .setName("orderServiceCB")
                                        .setFallbackUri("forward:/fallback/orders"))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter)
                                        .setKeyResolver(userKeyResolver))
                                .rewritePath("/api/orders/(?<segment>.*)", "/orders/${segment}")
                                .addRequestHeader("X-Gateway-Timestamp", String.valueOf(System.currentTimeMillis()))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3)
                                        .setMethods(HttpMethod.GET)
                                        .setStatuses(HttpStatus.INTERNAL_SERVER_ERROR,
                                                HttpStatus.BAD_GATEWAY,
                                                HttpStatus.SERVICE_UNAVAILABLE,
                                                HttpStatus.GATEWAY_TIMEOUT)))
                        .uri("lb://order-service"))

                .route("payment-service", r -> r
                        .path("/api/payments/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb
                                        .setName("paymentServiceCB")
                                        .setFallbackUri("forward:/fallback/payments"))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter)
                                        .setKeyResolver(userKeyResolver))
                                .rewritePath("/api/payments/(?<segment>.*)", "/payments/${segment}")
                                .addRequestHeader("X-Gateway-Timestamp", String.valueOf(System.currentTimeMillis()))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3)
                                        .setMethods(HttpMethod.GET)
                                        .setStatuses(HttpStatus.INTERNAL_SERVER_ERROR,
                                                HttpStatus.BAD_GATEWAY,
                                                HttpStatus.SERVICE_UNAVAILABLE,
                                                HttpStatus.GATEWAY_TIMEOUT)))
                        .uri("lb://payment-service"))

                .route("inventory-service", r -> r
                        .path("/api/inventory/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb
                                        .setName("inventoryCB")
                                        .setFallbackUri("forward:/fallback/inventory"))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter)
                                        .setKeyResolver(userKeyResolver))
                                .rewritePath("/api/inventory/(?<segment>.*)", "/inventory/${segment}")
                                .addRequestHeader("X-Gateway-Timestamp", String.valueOf(System.currentTimeMillis()))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3)
                                        .setMethods(HttpMethod.GET)
                                        .setStatuses(HttpStatus.INTERNAL_SERVER_ERROR,
                                                HttpStatus.BAD_GATEWAY,
                                                HttpStatus.SERVICE_UNAVAILABLE,
                                                HttpStatus.GATEWAY_TIMEOUT)))
                        .uri("lb://inventory-service"))

                .route("api-composition-service", r -> r
                        .path("/api/compositions/**")
                        .filters(f -> f
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter)
                                        .setKeyResolver(userKeyResolver))
                                .rewritePath("/api/compositions/(?<segment>.*)", "/compositions/${segment}")
                                .addRequestHeader("X-Gateway-Timestamp", String.valueOf(System.currentTimeMillis()))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3)
                                        .setMethods(HttpMethod.GET)
                                        .setStatuses(HttpStatus.INTERNAL_SERVER_ERROR,
                                                HttpStatus.BAD_GATEWAY,
                                                HttpStatus.SERVICE_UNAVAILABLE,
                                                HttpStatus.GATEWAY_TIMEOUT)))
                        .uri("lb://api-composition-service"))

                .route("strangler-fig-service", r -> r
                        .path("/api/legacy/**")
                        .filters(f -> f
                                .stripPrefix(2)
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter)
                                        .setKeyResolver(userKeyResolver))
                                .addRequestHeader("X-Gateway-Timestamp", String.valueOf(System.currentTimeMillis()))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3)
                                        .setMethods(HttpMethod.GET)
                                        .setStatuses(HttpStatus.INTERNAL_SERVER_ERROR,
                                                HttpStatus.BAD_GATEWAY,
                                                HttpStatus.SERVICE_UNAVAILABLE,
                                                HttpStatus.GATEWAY_TIMEOUT)))
                        .uri("lb://strangler-fig-service"))

                .build();
    }
}
