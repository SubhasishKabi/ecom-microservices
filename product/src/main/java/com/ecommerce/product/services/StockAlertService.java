package com.ecommerce.product.services;

import com.ecommerce.product.models.StockAlertSubscription;
import com.ecommerce.product.repositories.StockAlertSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockAlertService {

    private final StockAlertSubscriptionRepository stockAlertSubscriptionRepository;
    private final ProductService productService;

    /** Idempotent subscribe (not @Transactional for the same reason as WishlistService.addToWishlist). */
    public void subscribe(String userId, Long productId) {
        if (!productService.isActiveProduct(productId)) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }
        if (stockAlertSubscriptionRepository.existsByUserIdAndProductId(userId, productId)) {
            return;
        }
        try {
            stockAlertSubscriptionRepository.save(StockAlertSubscription.builder()
                    .userId(userId)
                    .productId(productId)
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.debug("User {} already subscribed to stock alerts for product {} (concurrent subscribe)", userId, productId);
        }
    }

    @Transactional
    public void unsubscribe(String userId, Long productId) {
        stockAlertSubscriptionRepository.deleteByUserIdAndProductId(userId, productId);
    }

    public boolean isSubscribed(String userId, Long productId) {
        return stockAlertSubscriptionRepository.existsByUserIdAndProductId(userId, productId);
    }
}
