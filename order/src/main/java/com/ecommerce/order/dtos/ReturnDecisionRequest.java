package com.ecommerce.order.dtos;

import lombok.Data;

@Data
public class ReturnDecisionRequest {
    private boolean approve;
    private String note;
}
