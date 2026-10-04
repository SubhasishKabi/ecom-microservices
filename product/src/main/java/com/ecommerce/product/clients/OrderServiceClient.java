package com.ecommerce.product.clients;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface OrderServiceClient {

    @GetExchange("/api/orders/verify-purchase")
    Boolean verifyPurchase(@RequestParam String userId, @RequestParam String productId);
}
