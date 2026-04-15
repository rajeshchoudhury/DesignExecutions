package com.microservices.notification.sidecar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Sidecar Logging Aspect - intercepts all service method calls to provide
 * centralized, structured logging with tracing context. In a real sidecar
 * deployment, this would be an Envoy/Istio filter or a separate log-shipping
 * container.
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class SidecarLoggingAspect {

    private final SidecarMetricsCollector metricsCollector;

    @Pointcut("within(com.microservices.notification.service..*)")
    public void serviceMethods() {}

    @Pointcut("within(com.microservices.notification.consumer..*)")
    public void consumerMethods() {}

    @Around("serviceMethods() || consumerMethods()")
    public Object logWithTracing(ProceedingJoinPoint joinPoint) throws Throwable {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
            MDC.put("correlationId", correlationId);
        }

        String spanId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("spanId", spanId);

        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String operation = className + "." + methodName;

        Instant start = Instant.now();
        log.info("[SIDECAR-LOG] >>> ENTER {} | correlationId={} | spanId={} | args={}",
                operation, correlationId, spanId, summarizeArgs(joinPoint.getArgs()));

        try {
            Object result = joinPoint.proceed();
            long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();

            log.info("[SIDECAR-LOG] <<< EXIT  {} | correlationId={} | spanId={} | durationMs={} | status=SUCCESS",
                    operation, correlationId, spanId, durationMs);

            metricsCollector.recordMethodExecution(operation, durationMs, true);
            return result;
        } catch (Throwable t) {
            long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();

            log.error("[SIDECAR-LOG] <<< EXIT  {} | correlationId={} | spanId={} | durationMs={} | status=FAILURE | error={}",
                    operation, correlationId, spanId, durationMs, t.getMessage());

            metricsCollector.recordMethodExecution(operation, durationMs, false);
            throw t;
        } finally {
            MDC.remove("spanId");
        }
    }

    private String summarizeArgs(Object[] args) {
        if (args == null || args.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < Math.min(args.length, 3); i++) {
            if (i > 0) sb.append(", ");
            if (args[i] == null) {
                sb.append("null");
            } else {
                String val = args[i].toString();
                sb.append(val.length() > 80 ? val.substring(0, 80) + "..." : val);
            }
        }
        if (args.length > 3) sb.append(", ...(").append(args.length - 3).append(" more)");
        sb.append("]");
        return sb.toString();
    }
}
