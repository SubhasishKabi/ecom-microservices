package com.ecommerce.payment.repositories;

import com.ecommerce.payment.models.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
    boolean existsByEventId(String eventId);

    Optional<ProcessedEvent> findByEventId(String eventId);
}
