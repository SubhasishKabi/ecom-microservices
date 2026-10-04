package com.ecommerce.notification.controllers;

import com.ecommerce.notification.dto.InternalNotificationRequest;
import com.ecommerce.notification.dto.NotificationResponse;
import com.ecommerce.notification.exceptions.ForbiddenException;
import com.ecommerce.notification.exceptions.UnauthorizedException;
import com.ecommerce.notification.security.JwtUtils;
import com.ecommerce.notification.services.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/notifications", "/api/notifications"})
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app user notifications and internal service-to-service notification API")
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationService notificationService;
    private final JwtUtils jwtUtils;

    @PostMapping("/internal")
    @Operation(summary = "Internal: create a notification for a user (service-to-service)",
            description = "Unauthenticated, trusted inter-service endpoint. Used e.g. by product-service to notify "
                    + "each stock-alert subscriber when a watched product is back in stock.")
    public ResponseEntity<NotificationResponse> createInternal(@Valid @RequestBody InternalNotificationRequest request) {
        NotificationResponse created = notificationService.createAndSend(
                request.getUserId().trim(),
                request.getType(),
                request.getTitle(),
                request.getMessage(),
                request.getReferenceId());
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping({"", "/"})
    @Operation(summary = "Get the current user's notifications (newest first, paged)")
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String userId = resolveUserId(userIdHeader, authHeader);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return ResponseEntity.ok(notificationService.getForUser(userId, unreadOnly, pageRequest));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get the number of unread notifications for the current user")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String userId = resolveUserId(userIdHeader, authHeader);
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread(userId)));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark one of the current user's notifications as read")
    public ResponseEntity<NotificationResponse> markRead(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String userId = resolveUserId(userIdHeader, authHeader);
        return ResponseEntity.ok(notificationService.markRead(id, userId));
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all of the current user's notifications as read")
    public ResponseEntity<Map<String, Integer>> markAllRead(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String userId = resolveUserId(userIdHeader, authHeader);
        return ResponseEntity.ok(Map.of("updated", notificationService.markAllRead(userId)));
    }

    /**
     * Same resolution order as user-service's AuthController: X-User-Id header first, then the JWT subject.
     * Additionally, when both are present they must agree, so an authenticated caller cannot read or modify
     * another user's notifications just by setting X-User-Id.
     */
    private String resolveUserId(String userIdHeader, String authHeader) {
        String jwtUserId = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtils.validateToken(token)) {
                jwtUserId = jwtUtils.extractUserId(token);
            }
        }
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            String headerUserId = userIdHeader.trim();
            if (jwtUserId != null && !jwtUserId.equals(headerUserId)) {
                throw new ForbiddenException("X-User-Id does not match the authenticated user");
            }
            return headerUserId;
        }
        if (jwtUserId != null && !jwtUserId.isBlank()) {
            return jwtUserId;
        }
        throw new UnauthorizedException("User ID could not be identified from request");
    }
}
