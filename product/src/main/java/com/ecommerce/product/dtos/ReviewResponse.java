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
public class ReviewResponse {
    private Long id;
    private Long productId;
    private String userId;
    private String userName;
    private Integer rating;
    private String comment;
    private Boolean verifiedPurchase;
    private LocalDateTime createdAt;
}
