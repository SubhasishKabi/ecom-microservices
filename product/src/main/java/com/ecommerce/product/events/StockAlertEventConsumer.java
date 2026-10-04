package com.ecommerce.product.events;

import com.ecommerce.product.clients.NotificationServiceClient;
import com.ecommerce.product.dtos.NotificationRequest;
import com.ecommerce.product.models.StockAlertSubscription;
import com.ecommerce.product.repositories.StockAlertSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.function.Consumer;

/**
 * Second, independent consumer group on inventory-service's existing "product-back-in-stock" topic
 * (notification-service consumes it separately). Notifies every subscriber of the product, then removes
 * the subscription (one-shot alert: users re-subscribe if they want to be told again next time).
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class StockAlertEventConsumer {

    private final StockAlertSubscriptionRepository stockAlertSubscriptionRepository;
    private final NotificationServiceClient notificationServiceClient;

    @Bean
    public Consumer<ProductBackInStockEvent> productBackInStock() {
        return event -> {
            if (event == null || event.getProductId() == null) {
                log.warn("Ignoring product-back-in-stock event without productId: {}", event);
                return;
            }
            List<StockAlertSubscription> subs = stockAlertSubscriptionRepository.findByProductId(event.getProductId());
            log.info("Product {} back in stock (eventId={}): notifying {} subscriber(s)",
                    event.getProductId(), event.getEventId(), subs.size());

            String productName = event.getProductName() != null ? event.getProductName() : "A product on your alert list";
            for (StockAlertSubscription sub : subs) {
                try {
                    notificationServiceClient.createNotification(new NotificationRequest(sub.getUserId(), "BACK_IN_STOCK",
                            "Back In Stock", productName + " is back in stock!", String.valueOf(event.getProductId())));
                    stockAlertSubscriptionRepository.delete(sub);
                } catch (Exception e) {
                    // Keep the subscription so the user is still alerted on the next restock event
                    log.warn("Failed to notify user {} for product {}: {}", sub.getUserId(), event.getProductId(), e.getMessage());
                }
            }
        };
    }
}
