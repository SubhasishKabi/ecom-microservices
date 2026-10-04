package com.ecommerce.notification.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductBackInStockEvent {
    private String eventId;
    private Long productId;
    private String productName;
    private Integer availableStock;
    private LocalDateTime timestamp;
}
