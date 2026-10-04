package com.ecommerce.order.repositories;

import com.ecommerce.order.models.Order;
import com.ecommerce.order.models.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Optional<Order> findByIdAndUserId(Long id, String userId);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    @Query("SELECT COUNT(o) > 0 FROM orders o JOIN o.items i WHERE o.userId = :userId AND i.productId = :productId " +
           "AND o.status IN ('CONFIRMED', 'SHIPPED', 'DELIVERED')")
    boolean existsVerifiedPurchase(@Param("userId") String userId, @Param("productId") String productId);
}
