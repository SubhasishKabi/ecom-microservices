package com.ecommerce.order.dtos;

import com.ecommerce.order.models.ReturnStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReturnResponse {
    private Long id;
    private Long orderId;
    private String userId;
    private String reason;
    private String adminNote;
    private ReturnStatus status;
    private LocalDateTime createdAt;
}
