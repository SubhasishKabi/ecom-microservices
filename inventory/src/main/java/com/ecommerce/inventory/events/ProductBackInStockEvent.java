package com.ecommerce.inventory.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Outbound event published to {@code product-back-in-stock} (binding {@code productBackInStock-out-0})
 * when a product's available stock transitions from 0 to a positive value.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBackInStockEvent {
    private String eventId;
    private String eventType;
    private Long productId;
    private String productName;
    private Integer availableStock;
    private LocalDateTime timestamp;
}
