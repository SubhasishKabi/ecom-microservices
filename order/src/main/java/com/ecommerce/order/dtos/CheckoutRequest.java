package com.ecommerce.order.dtos;

import lombok.Data;

@Data
public class CheckoutRequest {
    private Long addressId; // optional - falls back to the user's default address
}
