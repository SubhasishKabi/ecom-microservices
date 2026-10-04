package com.ecommerce.product.clients;

import com.ecommerce.product.dtos.NotificationRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface NotificationServiceClient {

    @PostExchange("/api/v1/notifications/internal")
    void createNotification(@RequestBody NotificationRequest request);
}
