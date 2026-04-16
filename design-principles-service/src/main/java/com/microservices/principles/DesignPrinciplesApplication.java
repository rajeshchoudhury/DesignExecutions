package com.microservices.principles;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the Design Principles Service.
 *
 * <p>This service is a teaching-oriented, production-grade Spring Boot application that
 * demonstrates six foundational software engineering principles through a realistic
 * Product Management domain:</p>
 *
 * <ul>
 *   <li><strong>SOC</strong> — Separation of Concerns: strict layered architecture
 *       (controller → service → repository → domain) with DTOs isolating API contracts
 *       from persistence models.</li>
 *   <li><strong>DYC</strong> — Document Your Code: comprehensive Javadoc, OpenAPI/Swagger
 *       annotations, custom documentation annotations, and Architecture Decision Records.</li>
 *   <li><strong>DRY</strong> — Don't Repeat Yourself: generic base entity, reusable
 *       specification builders, MapStruct mappers, AOP cross-cutting concerns, and
 *       template-method service patterns.</li>
 *   <li><strong>KISS</strong> — Keep It Simple, Stupid: strategy pattern for pricing,
 *       fluent builders, single-responsibility methods, and readable domain logic.</li>
 *   <li><strong>TDD</strong> — Test-Driven Development: unit tests, integration tests,
 *       ArchUnit architecture tests, test fixtures/builders, and BDD-style assertions.</li>
 *   <li><strong>YAGNI</strong> — You Ain't Gonna Need It: lean interfaces, no speculative
 *       abstractions, contrasted with "anti-pattern" examples in documentation.</li>
 * </ul>
 *
 * @author Design Patterns Platform Team
 * @version 1.0.0
 * @since 2024-04-15
 * @see <a href="docs/adr/">Architecture Decision Records</a>
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
public class DesignPrinciplesApplication {

    public static void main(String[] args) {
        SpringApplication.run(DesignPrinciplesApplication.class, args);
    }
}
