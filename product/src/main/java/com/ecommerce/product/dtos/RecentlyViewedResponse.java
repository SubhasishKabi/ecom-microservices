package com.ecommerce.product.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentlyViewedResponse {
    private Long productId;
    private LocalDateTime viewedAt;
    private ProductResponse product;
}
