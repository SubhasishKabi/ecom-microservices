package com.ecommerce.gateway;

import com.ecommerce.gateway.security.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates the JWT (same HS256 key issued by user-service) at the edge and forwards the
 * resolved identity downstream as X-User-Id / X-User-Role so every service can trust it
 * without re-parsing the token itself. Public (auth-free) and admin-only paths are enforced
 * here as a first line of defense; each backend service re-checks roles independently.
 */
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthFilter implements WebFilter {

    private final JwtUtils jwtUtils;

    // Paths that never require a token
    private static final List<Pattern> PUBLIC_PATHS = List.of(
            Pattern.compile("^/api/v1/auth/.*$"),
            Pattern.compile("^/api/auth/.*$"),
            Pattern.compile("^/actuator/.*$"),
            Pattern.compile("^/eureka/.*$"),
            Pattern.compile("^/swagger-ui.*$"),
            Pattern.compile("^/v3/api-docs.*$"),
            Pattern.compile("^/fallback/.*$")
    );

    // GET-only public browsing endpoints (storefront, must work without login)
    private static final List<Pattern> PUBLIC_GET_PATHS = List.of(
            Pattern.compile("^/api/v1/products.*$"),
            Pattern.compile("^/api/products.*$"),
            Pattern.compile("^/api/v1/inventory/.*$"),
            Pattern.compile("^/api/inventory/.*$"),
            Pattern.compile("^/api/orders/verify-purchase$"),
            Pattern.compile("^/api/v1/orders/verify-purchase$")
    );

    // Paths that require the ADMIN role, enforced at the gateway as well as each service
    private static final List<Pattern> ADMIN_PATHS = List.of(
            Pattern.compile("^/api/v1/orders/admin/.*$"),
            Pattern.compile("^/api/orders/admin/.*$"),
            Pattern.compile("^/api/v1/orders/returns/admin/.*$"),
            Pattern.compile("^/api/orders/returns/admin/.*$")
    );

    //The filter() method inside JwtAuthFilter is responsible for this authentication check
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        //An OPTIONS request is a request the browser sends to a server before the actual request to check whether the server allows it.
        if (request.getMethod() == HttpMethod.OPTIONS || isPublic(path)
                || (request.getMethod() == HttpMethod.GET && isPublicGet(path))) {
            // Strip any client-supplied identity headers on the unauthenticated path too,
            // so an anonymous caller can never inject X-User-Id/X-User-Role downstream.
            //For public requests, allow them without JWT, but remove any client-provided identity headers so the client cannot pretend to be another user or an admin.
            ServerHttpRequest strippedRequest = request.mutate()
                    .headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.remove("X-User-Role");
                    })
                    .build();
            return chain.filter(exchange.mutate().request(strippedRequest).build());
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        String token = authHeader.substring(7);
        if (!jwtUtils.validateToken(token)) {
            return unauthorized(exchange);
        }

        Claims claims = jwtUtils.extractAllClaims(token);
        String userId = claims.getSubject();
        String role = claims.get("role", String.class);

        if (isAdminPath(path) && !"ADMIN".equalsIgnoreCase(role)) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        // Use headers(...).set(...) rather than header(...) - the latter only ADDS a value,
        // which would leave a client-spoofed X-User-Id/X-User-Role in place ahead of ours
        // (Spring's @RequestHeader binds to the FIRST occurrence). set() replaces it outright.
        String resolvedUserId = userId;
        String resolvedRole = role != null ? role : "";
        ServerHttpRequest mutatedRequest = request.mutate()
                .headers(headers -> {
                    headers.set("X-User-Id", resolvedUserId);
                    headers.set("X-User-Role", resolvedRole);
                })
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream()
                        .anyMatch(p -> p.matcher(path).matches());
    }

    private boolean isPublicGet(String path) {
        return PUBLIC_GET_PATHS.stream()
                .anyMatch(p -> p.matcher(path).matches());
    }

    private boolean isAdminPath(String path) {
        return ADMIN_PATHS.stream().anyMatch(p -> p.matcher(path).matches());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
//WebFilter → comes from Spring WebFlux and works at the general WebFlux HTTP level.
//GlobalFilter → comes from Spring Cloud Gateway and is specifically for requests passing through the Gateway.