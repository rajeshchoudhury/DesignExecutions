package com.microservices.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);

        logRequest(request, correlationId);

        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> logResponse(exchange, correlationId, startTime)));
    }

    private void logRequest(ServerHttpRequest request, String correlationId) {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("type", "REQUEST");
        logEntry.put("correlationId", correlationId);
        logEntry.put("method", String.valueOf(request.getMethod()));
        logEntry.put("uri", request.getURI().toString());
        logEntry.put("path", request.getURI().getPath());
        logEntry.put("queryParams", request.getQueryParams().toSingleValueMap());
        logEntry.put("headers", sanitizeHeaders(request.getHeaders()));

        String contentLength = request.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH);
        logEntry.put("bodySize", contentLength != null ? contentLength + " bytes" : "unknown");

        log.info("Gateway Request: {}", formatAsJson(logEntry));
    }

    private void logResponse(ServerWebExchange exchange, String correlationId, long startTime) {
        long duration = System.currentTimeMillis() - startTime;

        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("type", "RESPONSE");
        logEntry.put("correlationId", correlationId);
        logEntry.put("status", String.valueOf(exchange.getResponse().getStatusCode()));
        logEntry.put("headers", exchange.getResponse().getHeaders().toSingleValueMap());
        logEntry.put("durationMs", duration);

        log.info("Gateway Response: {}", formatAsJson(logEntry));
    }

    private Map<String, String> sanitizeHeaders(HttpHeaders headers) {
        return headers.toSingleValueMap().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            String key = entry.getKey().toLowerCase();
                            if (key.equals("authorization") || key.equals("cookie") || key.equals("x-api-key")) {
                                return "***MASKED***";
                            }
                            return entry.getValue();
                        }
                ));
    }

    private String formatAsJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\": ");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else if (value instanceof Map) {
                sb.append(formatAsJson(castToStringMap((Map<?, ?>) value)));
            } else {
                sb.append(value);
            }
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToStringMap(Map<?, ?> map) {
        Map<String, Object> result = new HashMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
