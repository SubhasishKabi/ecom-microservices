package com.ecommerce.order.consumers;

import com.ecommerce.order.dtos.InventoryFailedEvent;
import com.ecommerce.order.dtos.InventoryReservedEvent;
import com.ecommerce.order.dtos.PaymentCompletedEvent;
import com.ecommerce.order.dtos.PaymentFailedEvent;
import com.ecommerce.order.services.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class OrderSagaConsumer {

    private final OrderService orderService;

    @Bean
    public Consumer<InventoryReservedEvent> inventoryReserved() {
        return event -> {
            log.info("Received InventoryReservedEvent for order {}", event.getOrderId());
            orderService.onInventoryReserved(event);
        };
    }

    @Bean
    public Consumer<InventoryFailedEvent> inventoryFailed() {
        return event -> {
            log.info("Received InventoryFailedEvent for order {}: {}", event.getOrderId(), event.getReason());
            orderService.onInventoryFailed(event);
        };
    }

    @Bean
    public Consumer<PaymentCompletedEvent> paymentCompleted() {
        return event -> {
            log.info("Received PaymentCompletedEvent for order {}", event.getOrderId());
            orderService.onPaymentCompleted(event);
        };
    }

    @Bean
    public Consumer<PaymentFailedEvent> paymentFailed() {
        return event -> {
            log.info("Received PaymentFailedEvent for order {}: {}", event.getOrderId(), event.getReason());
            orderService.onPaymentFailed(event);
        };
    }
}
