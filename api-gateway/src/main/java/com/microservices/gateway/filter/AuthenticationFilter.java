package com.microservices.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();
            String correlationId = request.getHeaders().getFirst("X-Correlation-Id");

            if (isOpenEndpoint(path, config.getOpenEndpoints())) {
                log.debug("[{}] Open endpoint accessed: {} {}", correlationId, request.getMethod(), path);
                return chain.filter(exchange);
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                log.warn("[{}] Missing or invalid Authorization header for: {} {}",
                        correlationId, request.getMethod(), path);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            String token = authHeader.substring(BEARER_PREFIX.length());

            try {
                TokenClaims claims = validateAndParseToken(token, config.getSecret());

                log.info("[{}] Authenticated request: {} {} by user={}",
                        correlationId, request.getMethod(), path, claims.userId);

                ServerHttpRequest modifiedRequest = request.mutate()
                        .header("X-User-Id", claims.userId)
                        .header("X-User-Roles", claims.roles)
                        .build();

                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            } catch (Exception e) {
                log.warn("[{}] Token validation failed for: {} {} - {}",
                        correlationId, request.getMethod(), path, e.getMessage());
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
        };
    }

    private boolean isOpenEndpoint(String path, List<String> openEndpoints) {
        if (openEndpoints == null) return false;
        return openEndpoints.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private TokenClaims validateAndParseToken(String token, String secret) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT structure");
        }

        String headerPayload = parts[0] + "." + parts[1];
        String signature = parts[2];

        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(secretKey);
            String computedSignature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(hmac.doFinal(headerPayload.getBytes(StandardCharsets.UTF_8)));

            if (!computedSignature.equals(signature)) {
                throw new IllegalArgumentException("Invalid token signature");
            }
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
            throw new IllegalArgumentException("Token validation error: " + e.getMessage());
        }

        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String userId = extractJsonField(payload, "sub");
        String roles = extractJsonField(payload, "roles");

        if (userId == null) {
            throw new IllegalArgumentException("Token missing 'sub' claim");
        }

        return new TokenClaims(userId, roles != null ? roles : "USER");
    }

    private String extractJsonField(String json, String field) {
        String searchKey = "\"" + field + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;

        int colonIndex = json.indexOf(':', keyIndex + searchKey.length());
        if (colonIndex == -1) return null;

        String remaining = json.substring(colonIndex + 1).trim();
        if (remaining.startsWith("\"")) {
            int endQuote = remaining.indexOf('"', 1);
            return endQuote > 0 ? remaining.substring(1, endQuote) : null;
        }

        int end = remaining.indexOf(',');
        if (end == -1) end = remaining.indexOf('}');
        return end > 0 ? remaining.substring(0, end).trim() : remaining.trim();
    }

    public static class Config {

        @Value("${gateway.auth.secret:default-secret-key-change-in-production}")
        private String secret;

        @Value("${gateway.auth.open-endpoints:/api/auth/**,/actuator/**,/fallback/**}")
        private List<String> openEndpoints;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public List<String> getOpenEndpoints() {
            return openEndpoints;
        }

        public void setOpenEndpoints(List<String> openEndpoints) {
            this.openEndpoints = openEndpoints;
        }
    }

    private record TokenClaims(String userId, String roles) {}
}
