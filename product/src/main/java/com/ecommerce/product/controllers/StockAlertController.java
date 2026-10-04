package com.ecommerce.product.controllers;

import com.ecommerce.product.security.CurrentUserResolver;
import com.ecommerce.product.services.StockAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/stock-alerts", "/api/stock-alerts"})
@Tag(name = "Stock Alerts", description = "\"Notify me when back in stock\" subscriptions (authentication required)")
public class StockAlertController {

    private final StockAlertService stockAlertService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/{productId}")
    @Operation(summary = "Subscribe to a back-in-stock alert for a product (idempotent)")
    public ResponseEntity<Map<String, Object>> subscribe(
            @PathVariable Long productId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = currentUserResolver.resolveUserId(userIdHeader, authHeader);
        stockAlertService.subscribe(userId, productId);
        return ResponseEntity.ok(Map.of("productId", productId, "subscribed", true));
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "Unsubscribe from a back-in-stock alert")
    public ResponseEntity<Void> unsubscribe(
            @PathVariable Long productId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = currentUserResolver.resolveUserId(userIdHeader, authHeader);
        stockAlertService.unsubscribe(userId, productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{productId}/subscribed")
    @Operation(summary = "Check whether the current user is subscribed to a product's back-in-stock alert")
    public ResponseEntity<Boolean> isSubscribed(
            @PathVariable Long productId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = currentUserResolver.resolveUserId(userIdHeader, authHeader);
        return ResponseEntity.ok(stockAlertService.isSubscribed(userId, productId));
    }
}
