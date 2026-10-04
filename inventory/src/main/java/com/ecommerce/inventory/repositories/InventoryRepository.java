package com.ecommerce.inventory.repositories;

import com.ecommerce.inventory.models.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductId(Long productId);

    /**
     * Row-locking variant (SELECT ... FOR UPDATE) used by every stock mutation so that concurrent
     * reservations / restocks on the same product cannot lose updates.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);

    List<Inventory> findByProductIdIn(List<Long> productIds);

    @Query("SELECT i FROM Inventory i WHERE (i.totalStock - i.reservedStock) <= i.lowStockThreshold")
    List<Inventory> findLowStockItems();
}
