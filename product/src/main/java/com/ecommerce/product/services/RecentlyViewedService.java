package com.ecommerce.product.services;

import com.ecommerce.product.dtos.ProductResponse;
import com.ecommerce.product.dtos.RecentlyViewedResponse;
import com.ecommerce.product.models.RecentlyViewed;
import com.ecommerce.product.repositories.RecentlyViewedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RecentlyViewedService {

    private final RecentlyViewedRepository recentlyViewedRepository;
    private final ProductService productService;

    /** Delete-then-insert so viewedAt always reflects the most recent view of this product. */
    @Transactional
    public void recordView(String userId, Long productId) {
        if (!productService.isActiveProduct(productId)) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }
        recentlyViewedRepository.deleteByUserIdAndProductId(userId, productId);
        recentlyViewedRepository.save(RecentlyViewed.builder()
                .userId(userId)
                .productId(productId)
                .viewedAt(LocalDateTime.now())
                .build());
    }

    public List<RecentlyViewedResponse> getRecentlyViewed(String userId) {
        List<RecentlyViewed> views = recentlyViewedRepository.findTop10ByUserIdOrderByViewedAtDesc(userId);
        Map<Long, ProductResponse> products = productService.getActiveProductsByIds(views.stream().map(RecentlyViewed::getProductId).toList());

        Set<Long> seen = new HashSet<>();
        return views.stream()
                .filter(v -> seen.add(v.getProductId())) // guard against duplicates from concurrent views
                .map(v -> {
                    ProductResponse product = products.get(v.getProductId());
                    if (product == null) {
                        return null; // product deleted/deactivated -> skip silently
                    }
                    return RecentlyViewedResponse.builder()
                            .productId(v.getProductId())
                            .viewedAt(v.getViewedAt())
                            .product(product)
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
