package com.ecommerce.order.dtos;

import com.ecommerce.order.models.OrderStatus;
import lombok.Data;

@Data
public class OrderStatusUpdateRequest {
    private OrderStatus status;
}
