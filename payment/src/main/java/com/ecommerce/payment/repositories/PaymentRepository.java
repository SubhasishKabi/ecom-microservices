package com.ecommerce.payment.repositories;

import com.ecommerce.payment.models.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    // Paged findAll(Pageable) is inherited from JpaRepository
    Optional<Payment> findByOrderId(Long orderId);

    /**
     * Row-locking lookup used by the refund paths so that a Kafka order-cancelled refund and a
     * REST admin refund (or two concurrent admin calls) for the same order cannot double-refund.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.orderId = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") Long orderId);
}
