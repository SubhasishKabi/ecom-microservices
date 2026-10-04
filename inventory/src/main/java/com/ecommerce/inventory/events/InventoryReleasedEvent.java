package com.ecommerce.inventory.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Outbound event published to {@code inventory-released} (binding {@code inventoryReleased-out-0})
 * after reserved stock for an order has been returned to the available pool.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReleasedEvent {
    private String eventId;
    private String eventType;
    private Long orderId;
    private String userId;
    private String reason;
    private String correlationId;
    private LocalDateTime timestamp;
}
