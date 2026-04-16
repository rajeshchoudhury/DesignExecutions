package com.microservices.principles.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 / Swagger UI configuration.
 *
 * <h3>DYC Principle — Living Documentation</h3>
 * <p>This configuration produces interactive API documentation at
 * {@code /swagger-ui.html} that is always in sync with the actual implementation.
 * Unlike a separate Wiki or Confluence page, this documentation <em>cannot drift</em>
 * from the code because it <em>is</em> the code.</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI designPrinciplesOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Design Principles Service API")
                        .version("1.0.0")
                        .description("""
                                Production-grade REST API demonstrating six software engineering principles:
                                
                                - **SOC** — Separation of Concerns: layered architecture with clear boundaries
                                - **DYC** — Document Your Code: this Swagger UI is generated from code annotations
                                - **DRY** — Don't Repeat Yourself: reusable specifications, mappers, and AOP
                                - **KISS** — Keep It Simple, Stupid: strategy pattern, focused endpoints
                                - **TDD** — Test-Driven Development: comprehensive test suite with 90%+ coverage
                                - **YAGNI** — You Ain't Gonna Need It: lean API surface, no speculative features
                                """)
                        .contact(new Contact()
                                .name("Design Patterns Platform Team")
                                .email("platform-team@example.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8085").description("Local development"),
                        new Server().url("http://design-principles-service:8085").description("Docker Compose")));
    }
}
