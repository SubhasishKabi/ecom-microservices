package com.ecommerce.product.controllers;

import com.ecommerce.product.dtos.RecentlyViewedResponse;
import com.ecommerce.product.security.CurrentUserResolver;
import com.ecommerce.product.services.RecentlyViewedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/recently-viewed", "/api/recently-viewed"})
@Tag(name = "Recently Viewed", description = "Per-user recently viewed products (authentication required)")
public class RecentlyViewedController {

    private final RecentlyViewedService recentlyViewedService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/{productId}")
    @Operation(summary = "Record a product view for the current user (called by the product detail page)")
    public ResponseEntity<Void> recordView(
            @PathVariable Long productId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = currentUserResolver.resolveUserId(userIdHeader, authHeader);
        recentlyViewedService.recordView(userId, productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping({"", "/"})
    @Operation(summary = "Get the current user's last 10 viewed products, most recent first")
    public ResponseEntity<List<RecentlyViewedResponse>> getRecentlyViewed(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = currentUserResolver.resolveUserId(userIdHeader, authHeader);
        return ResponseEntity.ok(recentlyViewedService.getRecentlyViewed(userId));
    }
}
