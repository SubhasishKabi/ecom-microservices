package com.ecommerce.inventory.consumers;

import com.ecommerce.inventory.events.InventoryEventPublisher;
import com.ecommerce.inventory.events.InventoryFailedEvent;
import com.ecommerce.inventory.events.InventoryReservedEvent;
import com.ecommerce.inventory.events.OrderCancelledEventIn;
import com.ecommerce.inventory.events.OrderCreatedEvent;
import com.ecommerce.inventory.events.PaymentCompletedEventIn;
import com.ecommerce.inventory.events.PaymentFailedEventIn;
import com.ecommerce.inventory.services.InventoryService;
import com.ecommerce.inventory.services.ReservationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Functional Spring Cloud Stream consumers. Bean names map to the config-server bindings:
 * <ul>
 *     <li>{@code orderCreated}   -> {@code orderCreated-in-0}   (topic order-created)</li>
 *     <li>{@code orderCancelled} -> {@code orderCancelled-in-0} (topic order-cancelled)</li>
 *     <li>{@code paymentFailed}  -> {@code paymentFailed-in-0}  (topic payment-failed)</li>
 * </ul>
 * Activated via {@code spring.cloud.function.definition} in application.yml.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumers {

    private final InventoryService inventoryService;
    private final InventoryEventPublisher eventPublisher;

    @Bean
    public Consumer<OrderCreatedEvent> orderCreated() {
        return event -> {
            log.info("Received OrderCreatedEvent {} for order {}", event.getEventId(), event.getOrderId());

            ReservationResult result = inventoryService.reserveForOrder(event);
            String correlationId = StringUtils.hasText(event.getEventId())
                    ? event.getEventId()
                    : String.valueOf(event.getOrderId());

            switch (result.outcome()) {
                case RESERVED -> eventPublisher.publish(InventoryEventPublisher.INVENTORY_RESERVED_BINDING,
                        InventoryReservedEvent.builder()
                                .eventId(UUID.randomUUID().toString())
                                .eventType("INVENTORY_RESERVED")
                                .orderId(event.getOrderId())
                                .userId(event.getUserId())
                                .correlationId(correlationId)
                                .items(event.getItems())
                                .totalAmount(event.getTotalAmount())
                                .timestamp(LocalDateTime.now())
                                .build());
                case REJECTED -> eventPublisher.publish(InventoryEventPublisher.INVENTORY_FAILED_BINDING,
                        InventoryFailedEvent.builder()
                                .eventId(UUID.randomUUID().toString())
                                .eventType("INVENTORY_FAILED")
                                .orderId(event.getOrderId())
                                .userId(event.getUserId())
                                .reason(result.reason())
                                .correlationId(correlationId)
                                .timestamp(LocalDateTime.now())
                                .build());
                case SKIPPED -> log.info("No inventory event published for order {}: {}",
                        event.getOrderId(), result.reason());
            }
        };
    }

    @Bean
    public Consumer<OrderCancelledEventIn> orderCancelled() {
        return event -> {
            log.info("Received OrderCancelledEvent {} for order {}", event.getEventId(), event.getOrderId());
            inventoryService.handleReleaseEvent(event.getEventId(), InventoryService.EVENT_ORDER_CANCELLED,
                    event.getOrderId(), describe("order cancelled", event.getReason()));
        };
    }

    @Bean
    public Consumer<PaymentFailedEventIn> paymentFailed() {
        return event -> {
            log.info("Received PaymentFailedEvent {} for order {}", event.getEventId(), event.getOrderId());
            inventoryService.handleReleaseEvent(event.getEventId(), InventoryService.EVENT_PAYMENT_FAILED,
                    event.getOrderId(), describe("payment failed", event.getReason()));
        };
    }

    @Bean
    public Consumer<PaymentCompletedEventIn> paymentCompleted() {
        return event -> {
            log.info("Received PaymentCompletedEvent {} for order {}", event.getEventId(), event.getOrderId());
            inventoryService.handlePaymentCompleted(event.getEventId(), event.getOrderId());
        };
    }

    private static String describe(String trigger, String detail) {
        return StringUtils.hasText(detail) ? trigger + ": " + detail : trigger;
    }
}
