package com.ecommerce.product.repositories;

import com.ecommerce.product.models.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<ProductReview, Long> {
    List<ProductReview> findByProductId(Long productId);

    Page<ProductReview> findByProductId(Long productId, Pageable pageable);

    Optional<ProductReview> findByProductIdAndUserId(Long productId, String userId);

    boolean existsByProductIdAndUserId(Long productId, String userId);
}
