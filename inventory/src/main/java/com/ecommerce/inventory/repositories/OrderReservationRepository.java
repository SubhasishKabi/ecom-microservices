package com.ecommerce.inventory.repositories;

import com.ecommerce.inventory.models.OrderReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderReservationRepository extends JpaRepository<OrderReservation, Long> {
    Optional<OrderReservation> findByOrderId(Long orderId);
}
