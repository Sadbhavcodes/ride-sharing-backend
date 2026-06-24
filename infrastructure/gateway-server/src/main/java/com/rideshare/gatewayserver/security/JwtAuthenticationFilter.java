package com.rideshare.gatewayserver.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Gateway-level JWT validation filter.
 *
 * <p>Every request flows through here. Requests to open endpoints (login / register)
 * are forwarded without any token check. All other requests must carry a valid
 * {@code Authorization: Bearer <token>} header — the gateway rejects them with
 * 401 Unauthorized if they don't.
 *
 * <p>No UserDetailsService is needed here; the gateway only verifies the token's
 * signature and extracts the subject, then forwards the userId downstream as an
 * {@code X-User-Id} header so downstream services can use it without re-validating.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    /** Paths that must reach downstream without a JWT (public endpoints). */
    private static final List<String> OPEN_PATHS = List.of(
            "/auth/login",
            "/auth/register"
    );

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public int getOrder() {
        // Run before routing so we can short-circuit on 401 before the request is routed
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Open paths: pass through without any token check
        if (isOpenPath(path)) {
            return chain.filter(exchange);
        }

        // Protected paths: require a valid Bearer token
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or malformed Authorization header");
        }

        String token = authHeader.substring(7);

        if (!jwtService.isTokenValid(token)) {
            return unauthorized(exchange, "Invalid or expired JWT token");
        }

        // Extract subject and forward it downstream as a custom header
        String userId = jwtService.extractSubject(token);

        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Id", userId)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isOpenPath(String path) {
        return OPEN_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("X-Auth-Error", reason);
        return response.setComplete();
    }
}
