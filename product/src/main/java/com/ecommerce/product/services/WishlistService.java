package com.ecommerce.product.services;

import com.ecommerce.product.dtos.ProductResponse;
import com.ecommerce.product.dtos.WishlistItemResponse;
import com.ecommerce.product.models.WishlistItem;
import com.ecommerce.product.repositories.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class WishlistService {

    private final WishlistItemRepository wishlistItemRepository;
    private final ProductService productService;

    public List<WishlistItemResponse> getWishlist(String userId) {
        List<WishlistItem> items = wishlistItemRepository.findByUserId(userId, Sort.by(Sort.Direction.DESC, "addedAt"));
        Map<Long, ProductResponse> products = productService.getActiveProductsByIds(
                items.stream().map(WishlistItem::getProductId).toList());

        return items.stream()
                .map(item -> {
                    ProductResponse product = products.get(item.getProductId());
                    if (product == null) {
                        return null; // product deleted/deactivated -> skip silently
                    }
                    return WishlistItemResponse.builder()
                            .id(item.getId())
                            .productId(item.getProductId())
                            .addedAt(item.getAddedAt())
                            .product(product)
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Idempotent add. Intentionally not @Transactional so a concurrent duplicate insert (unique constraint)
     * can be caught here without marking an outer transaction rollback-only.
     */
    public void addToWishlist(String userId, Long productId) {
        if (!productService.isActiveProduct(productId)) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }
        if (wishlistItemRepository.existsByUserIdAndProductId(userId, productId)) {
            return;
        }
        try {
            wishlistItemRepository.save(WishlistItem.builder().userId(userId).productId(productId).build());
        } catch (DataIntegrityViolationException e) {
            log.debug("Product {} already in wishlist of user {} (concurrent add)", productId, userId);
        }
    }

    @Transactional
    public void removeFromWishlist(String userId, Long productId) {
        wishlistItemRepository.deleteByUserIdAndProductId(userId, productId);
    }

    public boolean isInWishlist(String userId, Long productId) {
        return wishlistItemRepository.existsByUserIdAndProductId(userId, productId);
    }
}
