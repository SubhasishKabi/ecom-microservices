package com.ecommerce.order.clients;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.Map;

@HttpExchange
public interface PaymentServiceClient {

    // payment-service's refund endpoint is ADMIN-protected; the caller's bearer token
    // must be forwarded through since this call happens on behalf of an admin action.
    @PostExchange("/api/v1/payments/order/{orderId}/refund")
    void refund(@PathVariable Long orderId, @RequestHeader("Authorization") String authorization, @RequestBody Map<String, String> body);
}
