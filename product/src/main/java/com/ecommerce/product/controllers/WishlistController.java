package com.ecommerce.product.controllers;

import com.ecommerce.product.dtos.WishlistItemResponse;
import com.ecommerce.product.security.CurrentUserResolver;
import com.ecommerce.product.services.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/wishlist", "/api/wishlist"})
@Tag(name = "Wishlist", description = "Per-user wishlist APIs (authentication required)")
public class WishlistController {

    private final WishlistService wishlistService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping({"", "/"})
    @Operation(summary = "Get the current user's wishlist, enriched with current product details")
    public ResponseEntity<List<WishlistItemResponse>> getWishlist(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = currentUserResolver.resolveUserId(userIdHeader, authHeader);
        return ResponseEntity.ok(wishlistService.getWishlist(userId));
    }

    @PostMapping("/{productId}")
    @Operation(summary = "Add a product to the wishlist (idempotent)")
    public ResponseEntity<Map<String, Object>> addToWishlist(
            @PathVariable Long productId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = currentUserResolver.resolveUserId(userIdHeader, authHeader);
        wishlistService.addToWishlist(userId, productId);
        return ResponseEntity.ok(Map.of("productId", productId, "inWishlist", true));
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "Remove a product from the wishlist")
    public ResponseEntity<Void> removeFromWishlist(
            @PathVariable Long productId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = currentUserResolver.resolveUserId(userIdHeader, authHeader);
        wishlistService.removeFromWishlist(userId, productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{productId}/exists")
    @Operation(summary = "Check whether a product is in the current user's wishlist")
    public ResponseEntity<Boolean> isInWishlist(
            @PathVariable Long productId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = currentUserResolver.resolveUserId(userIdHeader, authHeader);
        return ResponseEntity.ok(wishlistService.isInWishlist(userId, productId));
    }
}
