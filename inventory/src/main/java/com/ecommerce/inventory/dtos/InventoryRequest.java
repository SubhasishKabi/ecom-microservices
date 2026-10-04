package com.ecommerce.inventory.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryRequest {

    @NotNull(message = "productId is required")
    @Positive(message = "productId must be positive")
    private Long productId;

    @Size(max = 255, message = "productName must be at most 255 characters")
    private String productName;

    @NotNull(message = "totalStock is required")
    @PositiveOrZero(message = "totalStock must be zero or greater")
    private Integer totalStock;

    /** Optional; defaults to 10 for new rows and is left unchanged for existing rows when omitted. */
    @PositiveOrZero(message = "lowStockThreshold must be zero or greater")
    private Integer lowStockThreshold;
}
