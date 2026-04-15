package com.microservices.composition.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${cache.order-details.ttl-seconds:30}")
    private int orderDetailsTtl;

    @Value("${cache.customer-dashboard.ttl-seconds:30}")
    private int customerDashboardTtl;

    @Value("${cache.fulfillment-status.ttl-seconds:15}")
    private int fulfillmentStatusTtl;

    @Value("${cache.max-size:500}")
    private int maxSize;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(orderDetailsTtl, TimeUnit.SECONDS)
                .maximumSize(maxSize)
                .recordStats());
        cacheManager.setCacheNames(java.util.List.of(
                "orderDetails", "customerDashboard", "fulfillmentStatus"));
        return cacheManager;
    }
}
