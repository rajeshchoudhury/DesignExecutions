package com.microservices.principles.annotation;

import java.lang.annotation.*;

/**
 * Marks a service method for automatic audit logging via AOP.
 *
 * <h3>DYC Principle — Self-Documenting Code</h3>
 * <p>When a developer sees {@code @Audited(operation = "CREATE_PRODUCT")} on a method,
 * they immediately understand that invocations are logged for audit purposes — without
 * reading the aspect implementation.</p>
 *
 * <h3>DRY Principle — Cross-Cutting Concerns</h3>
 * <p>Without this annotation + aspect, audit logging would be duplicated in every
 * service method. The AOP aspect handles timing, success/failure status, and structured
 * log output in a single place.</p>
 *
 * @see com.microservices.principles.aspect.AuditAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {

    /**
     * A machine-readable operation name for the audit log entry.
     * Convention: UPPER_SNAKE_CASE (e.g., "CREATE_PRODUCT", "RESERVE_STOCK").
     */
    String operation();
}
