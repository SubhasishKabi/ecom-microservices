package com.ecommerce.payment.consumers;

import com.ecommerce.payment.events.InventoryReservedEvent;
import com.ecommerce.payment.events.OrderCancelledEvent;
import com.ecommerce.payment.services.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Functional Spring Cloud Stream consumers. Wired via spring.cloud.function.definition
 * (inventoryReserved;orderCancelled) to bindings inventoryReserved-in-0 / orderCancelled-in-0.
 */
@Configuration
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final PaymentService paymentService;

    @Bean
    public Consumer<InventoryReservedEvent> inventoryReserved() {
        return paymentService::processPayment;
    }

    @Bean
    public Consumer<OrderCancelledEvent> orderCancelled() {
        return paymentService::handleOrderCancelled;
    }
}
