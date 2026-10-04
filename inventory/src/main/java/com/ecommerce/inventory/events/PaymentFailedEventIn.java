package com.ecommerce.inventory.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Inbound event consumed from {@code payment-failed} (binding {@code paymentFailed-in-0}).
 * Only {@code orderId} is required; reserved items are looked up from the local OrderReservation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEventIn {
    private String eventId;
    private Long orderId;
    private String userId;
    private String reason;
    private BigDecimal amount;
    private LocalDateTime timestamp;
}
