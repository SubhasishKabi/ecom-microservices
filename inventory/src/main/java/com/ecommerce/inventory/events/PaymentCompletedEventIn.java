package com.ecommerce.inventory.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Inbound event consumed from {@code payment-completed} (binding {@code paymentCompleted-in-0}).
 * Signals that a reservation should be permanently committed: reserved units are subtracted from
 * totalStock (ACTION_REDUCE) instead of staying reserved forever.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEventIn {
    private String eventId;
    private Long orderId;
    private String userId;
    private String paymentId;
    private BigDecimal amount;
    private LocalDateTime timestamp;
}
