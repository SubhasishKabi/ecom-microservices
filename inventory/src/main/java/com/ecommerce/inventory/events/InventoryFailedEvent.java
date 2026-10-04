package com.ecommerce.inventory.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryFailedEvent {
    private String eventId;
    private String eventType;
    private Long orderId;
    private String userId;
    private String reason;
    private String correlationId;
    private LocalDateTime timestamp;
}
