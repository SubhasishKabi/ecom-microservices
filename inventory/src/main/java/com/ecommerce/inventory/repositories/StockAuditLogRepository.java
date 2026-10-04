package com.ecommerce.inventory.repositories;

import com.ecommerce.inventory.models.StockAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockAuditLogRepository extends JpaRepository<StockAuditLog, Long> {
    List<StockAuditLog> findByProductIdOrderByTimestampDesc(Long productId);
}
