package com.ecommerce.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Gateway middleware that validates the JWT on every request, except for
 * configured public paths (e.g. the auth endpoints) and CORS preflight.
 * On success the caller identity is forwarded to downstream services via
 * trusted headers.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final SecretKey key;
    private final List<String> publicPaths;

    public JwtAuthenticationFilter(
            @Value("${jwt.secret}") String secret,
            @Value("${security.public-paths:/api/auth/}") String publicPaths) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.publicPaths = Arrays.stream(publicPaths.split(","))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (request.getMethod() == HttpMethod.OPTIONS || isPublic(request.getURI().getPath())) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Strip any client-supplied identity headers, then set trusted ones.
            ServerHttpRequest mutated = request.mutate()
                    .headers(h -> {
                        h.remove("X-Auth-User");
                        h.remove("X-Auth-Role");
                    })
                    .header("X-Auth-User", claims.getSubject())
                    .header("X-Auth-Role", String.valueOf(claims.get("role")))
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (JwtException | IllegalArgumentException ex) {
            return unauthorized(exchange, "Invalid or expired token");
        }
    }

    private boolean isPublic(String path) {
        return publicPaths.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        String body = "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}";
        var buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // Run before routing so unauthenticated requests never reach services.
        return -1;
    }
}
