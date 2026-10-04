package com.ecommerce.payment.controllers;

import com.ecommerce.payment.dtos.RefundRequest;
import com.ecommerce.payment.exceptions.PaymentNotFoundException;
import com.ecommerce.payment.models.Payment;
import com.ecommerce.payment.security.JwtUtils;
import com.ecommerce.payment.services.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping({"/api/v1/payments", "/api/payments"})
@Tag(name = "Payments", description = "Payment lookup and admin refund APIs")
public class PaymentController {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final PaymentService paymentService;
    private final JwtUtils jwtUtils;

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get the payment for an order (order owner or Admin)")
    public ResponseEntity<Payment> getPaymentByOrderId(
            @PathVariable Long orderId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            Authentication authentication) {

        Payment payment = paymentService.getPaymentByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException("No payment found for order " + orderId));

        if (!isAdmin(authentication)) {
            String callerId = resolveCallerId(userId, authHeader);
            if (callerId == null || !callerId.equals(payment.getUserId())) {
                log.warn("User {} attempted to access payment for orderId={} owned by another user", callerId, orderId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        return ResponseEntity.ok(payment);
    }

    @GetMapping({"", "/"})
    @Operation(summary = "List all payments with pagination (Admin)")
    public ResponseEntity<Page<Payment>> getAllPayments(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(paymentService.getAllPayments(pageable));
    }

    @PostMapping("/order/{orderId}/refund")
    @Operation(summary = "Refund the completed payment for an order (Admin, idempotent)")
    public ResponseEntity<Payment> refundPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody(required = false) RefundRequest request) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(paymentService.refundByOrderId(orderId, reason));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_ADMIN::equals);
    }

    /**
     * The verified JWT subject (the user id) is authoritative because it cannot be forged; the
     * X-User-Id header is only used as a fallback if the subject cannot be read.
     */
    private String resolveCallerId(String userIdHeader, String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtils.validateToken(token)) {
                String subject = jwtUtils.extractUserId(token);
                if (subject != null && !subject.isBlank()) {
                    return subject;
                }
            }
        }
        return (userIdHeader != null && !userIdHeader.isBlank()) ? userIdHeader.trim() : null;
    }
}
