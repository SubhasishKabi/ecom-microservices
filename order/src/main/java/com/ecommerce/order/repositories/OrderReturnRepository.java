package com.ecommerce.order.repositories;

import com.ecommerce.order.models.OrderReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderReturnRepository extends JpaRepository<OrderReturn, Long> {
    Page<OrderReturn> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
