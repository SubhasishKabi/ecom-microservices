package com.ecommerce.inventory.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservedEvent {
    private String eventId;
    private String eventType;
    private Long orderId;
    private String userId;
    private String correlationId;
    private List<OrderItemDTO> items;
    private BigDecimal totalAmount;
    private LocalDateTime timestamp;
}
