package com.ecommerce.product.repositories;

import com.ecommerce.product.models.RecentlyViewed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecentlyViewedRepository extends JpaRepository<RecentlyViewed, Long> {

    List<RecentlyViewed> findTop10ByUserIdOrderByViewedAtDesc(String userId);

    void deleteByUserIdAndProductId(String userId, Long productId);
}
