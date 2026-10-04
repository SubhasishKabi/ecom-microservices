package com.ecommerce.product.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBackInStockEvent {
    private String eventId;
    private Long productId;
    private String productName;
    private Integer availableStock;
    private LocalDateTime timestamp;
}
