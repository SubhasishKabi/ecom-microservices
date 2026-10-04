package com.ecommerce.user.controllers;

import com.ecommerce.user.dto.AddressDTO;
import com.ecommerce.user.dto.UserRequest;
import com.ecommerce.user.dto.UserResponse;
import com.ecommerce.user.security.JwtUtils;
import com.ecommerce.user.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/users", "/api/users"})
@Slf4j
@Tag(name = "User Management", description = "User profile, administration, and address management APIs")
public class UserController {

    private final UserService userService;
    private final JwtUtils jwtUtils;

    /**
     * A caller may act on their own id, or any id if they hold ROLE_ADMIN. Without this, any
     * authenticated user could edit or delete another user's profile/addresses just by id.
     */
    private void requireSelfOrAdmin(Long targetUserId,
                                     String userIdHeader,
                                     String authHeader) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return;
        }

        String callerId = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtils.validateToken(token)) {
                callerId = jwtUtils.extractUserId(token);
            }
        }
        if (callerId == null && userIdHeader != null && !userIdHeader.isBlank()) {
            callerId = userIdHeader.trim();
        }

        if (callerId == null || !callerId.equals(String.valueOf(targetUserId))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You may only manage your own account");
        }
    }

    @GetMapping
    @Operation(summary = "Get all users (supports pagination & search for admin)")
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10) Pageable pageable) {

        if (search != null && !search.isBlank()) {
            return ResponseEntity.ok(userService.searchUsers(search, pageable));
        }
        return ResponseEntity.ok(userService.fetchAllUsersPaged(pageable));
    }

    @GetMapping("/all")
    @Operation(summary = "Get unpaged list of all users")
    public ResponseEntity<List<UserResponse>> getAllUsersList() {
        return ResponseEntity.ok(userService.fetchAllUsers());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user details by ID")
    public ResponseEntity<UserResponse> getUser(@PathVariable String id) {
        log.info("Request received for user: {}", id);
        return userService.fetchUser(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create user directly (Admin or internal)")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest userRequest) {
        return new ResponseEntity<>(userService.addUser(userRequest), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user profile")
    public ResponseEntity<String> updateUser(@PathVariable String id,
                                             @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
                                             @RequestHeader(value = "Authorization", required = false) String authHeader,
                                             @RequestBody UserRequest updateUserRequest) {
        requireSelfOrAdmin(Long.valueOf(id), userIdHeader, authHeader);
        boolean updated = userService.updateUser(id, updateUserRequest);
        if (updated) {
            return ResponseEntity.ok("User updated successfully");
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Activate or deactivate a user account (Admin)")
    public ResponseEntity<String> toggleUserStatus(@PathVariable Long id, @RequestParam boolean active) {
        boolean updated = userService.toggleUserStatus(id, active);
        if (updated) {
            return ResponseEntity.ok("User status updated to: " + (active ? "ACTIVE" : "INACTIVE"));
        }
        return ResponseEntity.notFound().build();
    }

    // Addresses APIs
    @GetMapping("/{id}/addresses")
    @Operation(summary = "Get addresses for a user")
    public ResponseEntity<List<AddressDTO>> getUserAddresses(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserAddresses(id));
    }

    @PostMapping("/{id}/addresses")
    @Operation(summary = "Add a new address for a user")
    public ResponseEntity<AddressDTO> addAddress(@PathVariable Long id,
                                                 @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
                                                 @RequestHeader(value = "Authorization", required = false) String authHeader,
                                                 @Valid @RequestBody AddressDTO addressDTO) {
        requireSelfOrAdmin(id, userIdHeader, authHeader);
        return new ResponseEntity<>(userService.addAddress(id, addressDTO), HttpStatus.CREATED);
    }

    @PutMapping("/{id}/addresses/{addressId}")
    @Operation(summary = "Update an existing address")
    public ResponseEntity<AddressDTO> updateAddress(@PathVariable Long id,
                                                    @PathVariable Long addressId,
                                                    @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
                                                    @RequestHeader(value = "Authorization", required = false) String authHeader,
                                                    @Valid @RequestBody AddressDTO addressDTO) {
        requireSelfOrAdmin(id, userIdHeader, authHeader);
        return userService.updateAddress(id, addressId, addressDTO)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/addresses/{addressId}")
    @Operation(summary = "Delete an address")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id, @PathVariable Long addressId,
                                              @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
                                              @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireSelfOrAdmin(id, userIdHeader, authHeader);
        boolean deleted = userService.deleteAddress(id, addressId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}/addresses/{addressId}/default")
    @Operation(summary = "Set address as default")
    public ResponseEntity<String> setDefaultAddress(@PathVariable Long id, @PathVariable Long addressId,
                                                    @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
                                                    @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireSelfOrAdmin(id, userIdHeader, authHeader);
        boolean success = userService.setDefaultAddress(id, addressId);
        if (success) {
            return ResponseEntity.ok("Default address updated");
        }
        return ResponseEntity.notFound().build();
    }
}
