package com.ecommerce.order.controller;

import com.ecommerce.order.dtos.*;
import com.ecommerce.order.models.OrderStatus;
import com.ecommerce.order.services.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/orders", "/api/orders"})
@Tag(name = "Orders", description = "Checkout, order history, cancellation, and admin order management")
public class OrderController {
    private final OrderService orderService;

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @PostMapping("/checkout")
    @Operation(summary = "Place an order from the current cart")
    public ResponseEntity<OrderResponse> checkout(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody(required = false) CheckoutRequest request) {
        return new ResponseEntity<>(orderService.checkout(userId, request), HttpStatus.CREATED);
    }

    // Backward-compatible alias used by early cart->order flow
    @PostMapping
    @Operation(summary = "Place an order from the current cart (alias of /checkout)")
    public ResponseEntity<OrderResponse> createOrder(@RequestHeader("X-User-Id") String userId) {
        return new ResponseEntity<>(orderService.checkout(userId, null), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get the current user's order history")
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            @RequestHeader("X-User-Id") String userId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(orderService.getOrdersForUser(userId, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order details by ID")
    public ResponseEntity<OrderResponse> getOrder(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long id) {
        return orderService.getOrder(userId, id, isAdmin())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a pending/processing/confirmed order")
    public ResponseEntity<OrderResponse> cancelOrder(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long id,
            @RequestBody(required = false) CancelOrderRequest request) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(orderService.cancelOrder(userId, id, reason, isAdmin()));
    }

    @GetMapping("/verify-purchase")
    @Operation(summary = "Internal: check whether a user has a confirmed/delivered purchase of a product")
    public ResponseEntity<Boolean> verifyPurchase(@RequestParam String userId, @RequestParam String productId) {
        return ResponseEntity.ok(orderService.verifyPurchase(userId, productId));
    }

    // ---- Admin ----

    @GetMapping("/admin/all")
    @Operation(summary = "Admin: list all orders, optionally filtered by status")
    public ResponseEntity<Page<OrderResponse>> getAllOrdersAdmin(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orderService.getAllOrdersAdmin(status, pageable));
    }

    @PutMapping("/admin/{id}/status")
    @Operation(summary = "Admin: update order status (CONFIRMED -> SHIPPED -> DELIVERED)")
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable Long id, @RequestBody OrderStatusUpdateRequest request) {
        return ResponseEntity.ok(orderService.updateStatusAdmin(id, request.getStatus()));
    }

    // ---- Returns ----

    @PostMapping("/{id}/return")
    @Operation(summary = "Request a return for a delivered order")
    public ResponseEntity<ReturnResponse> requestReturn(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long id,
            @RequestBody ReturnRequest request) {
        return new ResponseEntity<>(orderService.requestReturn(userId, id, request.getReason()), HttpStatus.CREATED);
    }

    @GetMapping("/returns/my")
    @Operation(summary = "Get the current user's return requests")
    public ResponseEntity<Page<ReturnResponse>> getMyReturns(
            @RequestHeader("X-User-Id") String userId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(orderService.getReturnsForUser(userId, pageable));
    }

    @GetMapping("/returns/admin/all")
    @Operation(summary = "Admin: list all return requests")
    public ResponseEntity<Page<ReturnResponse>> getAllReturnsAdmin(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orderService.getAllReturnsAdmin(pageable));
    }

    @PutMapping("/returns/admin/{id}/decision")
    @Operation(summary = "Admin: approve or reject a return request (approval triggers a refund via payment-service)")
    public ResponseEntity<ReturnResponse> decideReturn(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @RequestBody ReturnDecisionRequest request) {
        return ResponseEntity.ok(orderService.decideReturnAdmin(id, request.isApprove(), request.getNote(), authorization));
    }
}
