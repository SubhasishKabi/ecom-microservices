package com.ecommerce.inventory.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestockRequest {

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be greater than zero")
    private Integer quantity;

    /** Optional free-text reference (e.g. PO number / admin id). Defaults to the authenticated admin. */
    @Size(max = 255, message = "reference must be at most 255 characters")
    private String reference;
}
