package com.ecommerce.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayConfig {

    @Value("${eureka.dashboard.url:http://localhost:8761}")
    private String eurekaDashboardUrl;

    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(10,20,1);
        //Means the rate limiter replenishes 10 tokens per second.
        //This is the maximum number of tokens the bucket can hold.
        //Each request requires 1 token.
    }

    @Bean
    public KeyResolver hostNameKeyResolver() {
        return exchange -> Mono.just(
                exchange.getRequest().getRemoteAddress().getHostName());
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("product-service", r -> r
                        .path("/api/products/**", "/api/v1/products/**")
                        .filters(f -> f.retry(retryConfig -> retryConfig
                                        .setRetries(10)
                                        .setMethods(HttpMethod.GET)
                                )
                                .requestRateLimiter(config -> config
                                        .setRateLimiter(redisRateLimiter())
                                        //The RedisRateLimiter keeps track of how many requests have been allowed.
                                        .setKeyResolver(hostNameKeyResolver()))
                                .circuitBreaker(config -> config
                                        .setName("ecomBreaker")
                                        .setFallbackUri("forward:/fallback/products")))
//                        .filters(f -> f.rewritePath("/products(?<segment>/?.*)",
//                                "/api/products${segment}"))
                        .uri("lb://PRODUCT-SERVICE"))

                .route("user-service", r -> r
                        .path("/api/users/**", "/api/v1/users/**", "/api/v1/auth/**", "/api/auth/**")
//                        .filters(f -> f.rewritePath("/users(?<segment>/?.*)",
//                                "/api/users${segment}"))
                        .uri("lb://USER-SERVICE"))

                .route("order-service", r -> r
                        .path("/api/orders/**", "/api/v1/orders/**", "/api/cart/**")
//                        .filters(f -> f.rewritePath("/(?<segment>.*)",
//                                "/api/${segment}"))
                        .uri("lb://ORDER-SERVICE"))

                .route("product-user-features", r -> r
                        .path("/api/wishlist/**", "/api/v1/wishlist/**",
                              "/api/recently-viewed/**", "/api/v1/recently-viewed/**",
                              "/api/stock-alerts/**", "/api/v1/stock-alerts/**")
                        .uri("lb://PRODUCT-SERVICE"))

                .route("inventory-service", r -> r
                        .path("/api/inventory/**", "/api/v1/inventory/**")
                        .uri("lb://INVENTORY-SERVICE"))

                .route("payment-service", r -> r
                        .path("/api/payments/**", "/api/v1/payments/**")
                        .uri("lb://PAYMENT-SERVICE"))

                .route("notification-service", r -> r
                        .path("/api/notifications/**", "/api/v1/notifications/**")
                        .uri("lb://NOTIFICATION-SERVICE"))

                .route("eureka-server", r -> r
                        .path("/eureka/main")
                        .filters(f -> f.rewritePath("/eureka/main", "/"))
                        .uri(eurekaDashboardUrl))

                .route("eureka-server-static", r -> r
                        .path("/eureka/**")
                        .uri(eurekaDashboardUrl))
                .build();
    }
}
