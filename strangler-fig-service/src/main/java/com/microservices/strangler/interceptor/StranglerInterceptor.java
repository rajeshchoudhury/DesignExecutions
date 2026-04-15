package com.microservices.strangler.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.strangler.facade.MigrationMetrics;
import com.microservices.strangler.facade.RoutingStrategy;
import com.microservices.strangler.facade.StranglerFacade;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Intercepts all /api/** requests and delegates routing decisions to the
 * StranglerFacade. This interceptor sits at the HTTP layer and logs every
 * routing decision with a correlation ID for traceability.
 *
 * For requests that match strangler-managed endpoints, the interceptor
 * determines which implementation (legacy or modern) should handle the
 * request based on the facade's routing configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StranglerInterceptor implements HandlerInterceptor {

    private final StranglerFacade stranglerFacade;
    private final MigrationMetrics metrics;
    private final ObjectMapper objectMapper;

    private static final String ATTR_START_TIME = "strangler.startTime";
    private static final String ATTR_CORRELATION_ID = "strangler.correlationId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put("correlationId", correlationId);
        request.setAttribute(ATTR_CORRELATION_ID, correlationId);
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());

        String path = request.getRequestURI();
        String method = request.getMethod();

        if (isStranglerManagedPath(path)) {
            String endpoint = resolveEndpoint(path, method);
            RoutingStrategy strategy = stranglerFacade.getRoutingStrategies()
                    .getOrDefault(endpoint, RoutingStrategy.LEGACY_ONLY);
            int modernPct = stranglerFacade.getModernTrafficPercentage()
                    .getOrDefault(endpoint, 0);

            log.info("[STRANGLER-INTERCEPTOR] {} {} | correlationId={} | endpoint={} | " +
                            "strategy={} | modernPct={}%",
                    method, path, correlationId, endpoint, strategy, modernPct);

            response.setHeader("X-Correlation-Id", correlationId);
            response.setHeader("X-Routing-Strategy", strategy.name());
            response.setHeader("X-Modern-Traffic-Pct", String.valueOf(modernPct));
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(ATTR_START_TIME);
        String correlationId = (String) request.getAttribute(ATTR_CORRELATION_ID);

        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            String path = request.getRequestURI();

            log.info("[STRANGLER-INTERCEPTOR] Completed {} {} | correlationId={} | " +
                            "status={} | durationMs={} | error={}",
                    request.getMethod(), path, correlationId,
                    response.getStatus(), duration,
                    ex != null ? ex.getMessage() : "none");
        }

        MDC.remove("correlationId");
    }

    private boolean isStranglerManagedPath(String path) {
        return path.startsWith("/api/orders") || path.startsWith("/api/v2/orders")
                || path.startsWith("/api/legacy/orders");
    }

    private String resolveEndpoint(String path, String method) {
        boolean hasId = path.matches(".*/orders/[^/]+$");

        return switch (method.toUpperCase()) {
            case "POST" -> "createOrder";
            case "GET" -> hasId ? "getOrder" : "getAllOrders";
            case "PUT" -> "updateOrder";
            case "DELETE" -> "deleteOrder";
            default -> "unknown";
        };
    }
}
