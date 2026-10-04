package com.ecommerce.payment.dtos;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {
    @Size(max = 255, message = "Refund reason must be at most 255 characters")
    private String reason;
}
