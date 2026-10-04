package com.ecommerce.inventory.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Inbound event consumed from {@code order-cancelled} (binding {@code orderCancelled-in-0}).
 * Only {@code orderId} is required; reserved items are looked up from the local OrderReservation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelledEventIn {
    private String eventId;
    private Long orderId;
    private String userId;
    private String reason;
    private LocalDateTime timestamp;
}
