package com.ecommerce.notification.consumers;

import com.ecommerce.notification.models.NotificationType;
import com.ecommerce.notification.payload.*;
import com.ecommerce.notification.services.EmailService;
import com.ecommerce.notification.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.function.Consumer;

/**
 * Spring Cloud Stream functional consumers. Bean names must match
 * spring.cloud.function.definition and the "<name>-in-0" bindings defined in
 * configserver/.../notification-service.yml (Kafka topics order-created, order-cancelled, ...).
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private static final String STOCK_ALERT_RECIPIENT = "stock-alert-subscribers";

    private final NotificationService notificationService;
    private final EmailService emailService;

    @Bean
    public Consumer<OrderCreatedEvent> orderCreated() {
        return e -> {
            log.info("Received order-created event {} for order {} (user {})", e.getEventId(), e.getOrderId(), e.getUserId());
            createNotification(e.getUserId(), NotificationType.ORDER_CREATED, "Order Placed",
                    "Your order #" + e.getOrderId() + " has been placed successfully.", e.getOrderId());
        };
    }

    @Bean
    public Consumer<OrderCancelledEvent> orderCancelled() {
        return e -> {
            log.info("Received order-cancelled event {} for order {} (user {})", e.getEventId(), e.getOrderId(), e.getUserId());
            createNotification(e.getUserId(), NotificationType.ORDER_CANCELLED, "Order Cancelled",
                    "Your order #" + e.getOrderId() + " was cancelled. Reason: " + orDefault(e.getReason(), "not specified"),
                    e.getOrderId());
        };
    }

    @Bean
    public Consumer<OrderShippedEvent> orderShipped() {
        return e -> {
            log.info("Received order-shipped event {} for order {} (user {})", e.getEventId(), e.getOrderId(), e.getUserId());
            createNotification(e.getUserId(), NotificationType.ORDER_SHIPPED, "Order Shipped",
                    "Good news! Your order #" + e.getOrderId() + " has been shipped.", e.getOrderId());
        };
    }

    @Bean
    public Consumer<OrderDeliveredEvent> orderDelivered() {
        return e -> {
            log.info("Received order-delivered event {} for order {} (user {})", e.getEventId(), e.getOrderId(), e.getUserId());
            createNotification(e.getUserId(), NotificationType.ORDER_DELIVERED, "Order Delivered",
                    "Your order #" + e.getOrderId() + " has been delivered. Enjoy!", e.getOrderId());
        };
    }

    @Bean
    public Consumer<PaymentCompletedEvent> paymentCompleted() {
        return e -> {
            log.info("Received payment-completed event {} for order {} (user {}, payment {})",
                    e.getEventId(), e.getOrderId(), e.getUserId(), e.getPaymentId());
            createNotification(e.getUserId(), NotificationType.ORDER_CONFIRMED, "Payment Received",
                    "We received your payment of " + amount(e.getAmount()) + " for order #" + e.getOrderId()
                            + ". Your order is confirmed.",
                    e.getOrderId());
        };
    }

    @Bean
    public Consumer<PaymentFailedEvent> paymentFailed() {
        return e -> {
            log.info("Received payment-failed event {} for order {} (user {})", e.getEventId(), e.getOrderId(), e.getUserId());
            createNotification(e.getUserId(), NotificationType.PAYMENT_FAILED, "Payment Failed",
                    "Your payment of " + amount(e.getAmount()) + " for order #" + e.getOrderId()
                            + " failed. Reason: " + orDefault(e.getReason(), "not specified") + ". Please try again.",
                    e.getOrderId());
        };
    }

    @Bean
    public Consumer<RefundCompletedEvent> refundCompleted() {
        return e -> {
            log.info("Received refund-completed event {} for order {} (user {}, refund {})",
                    e.getEventId(), e.getOrderId(), e.getUserId(), e.getRefundId());
            createNotification(e.getUserId(), NotificationType.REFUND_COMPLETED, "Refund Completed",
                    "Your refund of " + amount(e.getAmount()) + " for order #" + e.getOrderId()
                            + " has been processed (refund ID: " + orDefault(e.getRefundId(), "N/A") + ").",
                    e.getOrderId());
        };
    }

    /**
     * Product-wide event with no userId: only the email/console broadcast is done here.
     * Per-user in-app notifications are created by product-service calling POST /api/v1/notifications/internal
     * for each of its stock-alert subscribers.
     */
    @Bean
    public Consumer<ProductBackInStockEvent> productBackInStock() {
        return e -> {
            log.info("Received product-back-in-stock event {} for product {} ({}), available stock {}",
                    e.getEventId(), e.getProductId(), e.getProductName(), e.getAvailableStock());
            emailService.sendEmail(STOCK_ALERT_RECIPIENT, "Back In Stock",
                    orDefault(e.getProductName(), "Product #" + e.getProductId()) + " is back in stock!");
        };
    }

    private void createNotification(String userId, NotificationType type, String title, String message, Long orderId) {
        if (userId == null || userId.isBlank()) {
            // Retrying would never succeed (userId is NOT NULL), so drop with a warning instead of failing the binding
            log.warn("Skipping {} notification for order {}: event has no userId", type, orderId);
            return;
        }
        notificationService.createAndSend(userId, type, title, message, String.valueOf(orderId));
    }

    private static String amount(BigDecimal amount) {
        return amount != null ? amount.toPlainString() : "N/A";
    }

    private static String orDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
