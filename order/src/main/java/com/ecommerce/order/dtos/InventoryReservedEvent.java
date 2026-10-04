package com.ecommerce.order.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
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
