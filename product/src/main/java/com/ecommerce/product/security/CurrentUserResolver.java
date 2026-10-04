package com.ecommerce.product.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the calling user's id the same way user-service's AuthController.resolveUserId does:
 * the X-User-Id header first, falling back to the subject of the Bearer JWT.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final JwtUtils jwtUtils;

    public String resolveUserId(String userIdHeader, String authHeader) {
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            return userIdHeader.trim();
        }
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtils.validateToken(token)) {
                return jwtUtils.extractUserId(token);
            }
        }
        throw new IllegalArgumentException("User ID could not be identified from request");
    }
}
