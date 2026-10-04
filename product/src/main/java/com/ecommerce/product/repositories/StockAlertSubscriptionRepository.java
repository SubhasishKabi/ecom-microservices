package com.ecommerce.product.repositories;

import com.ecommerce.product.models.StockAlertSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockAlertSubscriptionRepository extends JpaRepository<StockAlertSubscription, Long> {

    boolean existsByUserIdAndProductId(String userId, Long productId);

    void deleteByUserIdAndProductId(String userId, Long productId);

    List<StockAlertSubscription> findByProductId(Long productId);
}
