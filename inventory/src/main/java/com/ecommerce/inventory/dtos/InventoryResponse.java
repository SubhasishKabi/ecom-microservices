package com.ecommerce.inventory.dtos;

import com.ecommerce.inventory.models.Inventory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {
    private Long productId;
    private String productName;
    private Integer totalStock;
    private Integer reservedStock;
    private Integer availableStock;
    private Integer lowStockThreshold;

    public static InventoryResponse from(Inventory inventory) {
        return InventoryResponse.builder()
                .productId(inventory.getProductId())
                .productName(inventory.getProductName())
                .totalStock(inventory.getTotalStock())
                .reservedStock(inventory.getReservedStock())
                .availableStock(inventory.getAvailableStock())
                .lowStockThreshold(inventory.getLowStockThreshold())
                .build();
    }
}
