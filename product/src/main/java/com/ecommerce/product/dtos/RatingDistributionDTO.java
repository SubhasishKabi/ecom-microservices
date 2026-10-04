package com.ecommerce.product.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingDistributionDTO {
    private Double averageRating;
    private Integer totalReviews;
    private Map<Integer, Long> distribution; // e.g. 5 -> 10, 4 -> 3, 3 -> 1, 2 -> 0, 1 -> 0
}
