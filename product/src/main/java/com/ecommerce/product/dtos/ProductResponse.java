package com.ecommerce.product.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private BigDecimal discount;
    private BigDecimal effectivePrice;
    private String brand;
    private String category;
    private Integer stockQuantity;
    private String imageUrl;
    private Boolean active;
    private Double rating;
    private Integer reviewCount;
    private LocalDateTime createdAt;
}
