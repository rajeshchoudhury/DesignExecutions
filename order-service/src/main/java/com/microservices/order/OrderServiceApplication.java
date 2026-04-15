package com.microservices.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Entry point for the Order Service.
 *
 * This service is the centerpiece of the microservices platform, demonstrating:
 * <ul>
 *   <li><strong>Event Sourcing</strong> — aggregate state derived from replayed events</li>
 *   <li><strong>CQRS</strong> — separate command and query models with independent scaling</li>
 *   <li><strong>Saga Orchestration</strong> — cross-service distributed transaction management</li>
 * </ul>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
