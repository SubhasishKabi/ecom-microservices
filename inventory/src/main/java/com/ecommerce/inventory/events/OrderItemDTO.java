package com.ecommerce.inventory.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Order line item as published by order-service.
 * NOTE: {@code productId} is the String form of the product's numeric ID; convert with
 * {@code Long.valueOf(productId)} before looking up {@code Inventory} rows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDTO {
    private Long id;
    private String productId;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal subTotal;
}
