package com.ecommerce.order.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
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
