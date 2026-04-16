package com.microservices.principles.aspect;

import com.microservices.principles.annotation.Audited;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * AOP aspect that intercepts methods annotated with {@link Audited} to produce
 * structured audit log entries.
 *
 * <h3>DRY Principle — Cross-Cutting Concern</h3>
 * <p>Audit logging is a textbook cross-cutting concern. Without AOP, every service method
 * would need:</p>
 * <pre>{@code
 * log.info("AUDIT | operation=CREATE_PRODUCT | started");
 * try {
 *     // business logic
 *     log.info("AUDIT | operation=CREATE_PRODUCT | success | duration=42ms");
 * } catch (Exception e) {
 *     log.error("AUDIT | operation=CREATE_PRODUCT | failure | error=...");
 *     throw e;
 * }
 * }</pre>
 * <p>This aspect eliminates that duplication — methods just declare {@code @Audited}.</p>
 *
 * <h3>SOC Principle</h3>
 * <p>Audit infrastructure is completely separated from business logic. The service
 * doesn't know or care that it's being audited.</p>
 *
 * @see Audited
 */
@Aspect
@Component
@Slf4j
public class AuditAspect {

    /**
     * Intercepts any method annotated with {@link Audited}, logging the operation
     * name, execution duration, and success/failure status.
     *
     * @param joinPoint the intercepted method invocation
     * @param audited   the annotation instance carrying the operation name
     * @return the original method's return value
     * @throws Throwable any exception thrown by the intercepted method (re-thrown as-is)
     */
    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        String operation = audited.operation();
        String method = ((MethodSignature) joinPoint.getSignature()).toShortString();
        Instant start = Instant.now();

        log.info("AUDIT | operation={} | method={} | status=STARTED", operation, method);

        try {
            Object result = joinPoint.proceed();
            Duration duration = Duration.between(start, Instant.now());

            log.info("AUDIT | operation={} | method={} | status=SUCCESS | duration={}ms",
                    operation, method, duration.toMillis());

            return result;
        } catch (Exception ex) {
            Duration duration = Duration.between(start, Instant.now());

            log.error("AUDIT | operation={} | method={} | status=FAILURE | duration={}ms | error={}",
                    operation, method, duration.toMillis(), ex.getMessage());

            throw ex;
        }
    }
}
