package com.ecommerce.product.services;

import com.ecommerce.product.clients.OrderServiceClient;
import com.ecommerce.product.dtos.*;
import com.ecommerce.product.models.Product;
import com.ecommerce.product.models.ProductReview;
import com.ecommerce.product.repositories.ProductRepository;
import com.ecommerce.product.repositories.ReviewRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final OrderServiceClient orderServiceClient;

    @PostConstruct
    public void seedInitialProducts() {
        if (productRepository.count() == 0) {
            log.info("Seeding initial product catalog...");
            List<Product> catalog = List.of(
                    Product.builder()
                            .name("Wireless Noise-Canceling Headphones")
                            .description("Premium over-ear wireless headphones with active noise cancellation, 30h battery life, and high-resolution sound.")
                            .price(new BigDecimal("299.99"))
                            .discount(new BigDecimal("15.00"))
                            .brand("Sony")
                            .category("Electronics")
                            .stockQuantity(45)
                            .imageUrl("https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=600&auto=format&fit=crop&q=80")
                            .active(true)
                            .rating(4.8)
                            .reviewCount(128)
                            .build(),
                    Product.builder()
                            .name("Smart Fitness Watch Pro")
                            .description("AMOLED touch display, GPS, continuous heart rate, sleep tracking, and 7-day battery life. Water resistant up to 50m.")
                            .price(new BigDecimal("199.50"))
                            .discount(new BigDecimal("10.00"))
                            .brand("Garmin")
                            .category("Electronics")
                            .stockQuantity(30)
                            .imageUrl("https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=600&auto=format&fit=crop&q=80")
                            .active(true)
                            .rating(4.6)
                            .reviewCount(85)
                            .build(),
                    Product.builder()
                            .name("Ergonomic Mechanical Keyboard")
                            .description("RGB backlit mechanical keyboard with hot-swappable switches, PBT keycaps, and aircraft-grade aluminum frame.")
                            .price(new BigDecimal("129.00"))
                            .discount(new BigDecimal("5.00"))
                            .brand("Keychron")
                            .category("Electronics")
                            .stockQuantity(60)
                            .imageUrl("https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=600&auto=format&fit=crop&q=80")
                            .active(true)
                            .rating(4.7)
                            .reviewCount(94)
                            .build(),
                    Product.builder()
                            .name("Classic Oxford Leather Shoes")
                            .description("Handcrafted genuine full-grain leather oxford dress shoes with cushioned insole and Goodyear welted sole.")
                            .price(new BigDecimal("149.99"))
                            .discount(new BigDecimal("20.00"))
                            .brand("Clarks")
                            .category("Fashion")
                            .stockQuantity(25)
                            .imageUrl("https://images.unsplash.com/photo-1549298916-b41d501d3772?w=600&auto=format&fit=crop&q=80")
                            .active(true)
                            .rating(4.5)
                            .reviewCount(52)
                            .build(),
                    Product.builder()
                            .name("Minimalist Canvas Backpack")
                            .description("Water-resistant vintage canvas backpack with padded 15.6-inch laptop compartment and leather accents.")
                            .price(new BigDecimal("69.95"))
                            .discount(BigDecimal.ZERO)
                            .brand("Fjallraven")
                            .category("Fashion")
                            .stockQuantity(80)
                            .imageUrl("https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&auto=format&fit=crop&q=80")
                            .active(true)
                            .rating(4.6)
                            .reviewCount(64)
                            .build(),
                    Product.builder()
                            .name("Stainless Steel Pour-Over Coffee Maker")
                            .description("Double-wall vacuum insulated coffee press with fine mesh stainless steel filter for barista quality brew at home.")
                            .price(new BigDecimal("49.99"))
                            .discount(new BigDecimal("10.00"))
                            .brand("Bodum")
                            .category("Home & Kitchen")
                            .stockQuantity(110)
                            .imageUrl("https://images.unsplash.com/photo-1517668808822-9ebb02f2a0e6?w=600&auto=format&fit=crop&q=80")
                            .active(true)
                            .rating(4.9)
                            .reviewCount(175)
                            .build(),
                    Product.builder()
                            .name("Non-Stick Cast Iron Skillet 12-inch")
                            .description("Pre-seasoned heavy-duty cast iron skillet for searing, baking, broiling, and frying on all cooktops.")
                            .price(new BigDecimal("39.95"))
                            .discount(BigDecimal.ZERO)
                            .brand("Lodge")
                            .category("Home & Kitchen")
                            .stockQuantity(70)
                            .imageUrl("https://images.unsplash.com/photo-1590794056226-79ef3a8147e1?w=600&auto=format&fit=crop&q=80")
                            .active(true)
                            .rating(4.8)
                            .reviewCount(210)
                            .build(),
                    Product.builder()
                            .name("Ultra-Lightweight Running Shoes")
                            .description("Breathable mesh upper with responsive foam cushioning for road running and marathon training.")
                            .price(new BigDecimal("119.99"))
                            .discount(new BigDecimal("25.00"))
                            .brand("Nike")
                            .category("Sports")
                            .stockQuantity(40)
                            .imageUrl("https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&auto=format&fit=crop&q=80")
                            .active(true)
                            .rating(4.7)
                            .reviewCount(320)
                            .build()
            );
            productRepository.saveAll(catalog);
            log.info("Seeded {} initial products successfully.", catalog.size());
        }
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findByActiveTrue().stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());
    }

    public Page<ProductResponse> getProductsPaged(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable)
                .map(this::mapToProductResponse);
    }

    public Page<ProductResponse> filterProducts(
            String keyword,
            String category,
            String brand,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Double minRating,
            Boolean inStock,
            Pageable pageable) {
        //It is used when you need to build dynamic database queries based on optional filters. Since any of these can be present or absent, writing a separate repository method for every combination would be messy.
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));

            if (keyword != null && !keyword.isBlank()) {
                String searchLike = "%" + keyword.toLowerCase().trim() + "%";
                Predicate nameLike = cb.like(cb.lower(root.get("name")), searchLike);
                Predicate descLike = cb.like(cb.lower(root.get("description")), searchLike);
                Predicate catLike = cb.like(cb.lower(root.get("category")), searchLike);
                Predicate brandLike = cb.like(cb.lower(root.get("brand")), searchLike);
                predicates.add(cb.or(nameLike, descLike, catLike, brandLike));
            }

            if (category != null && !category.isBlank() && !category.equalsIgnoreCase("all")) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.toLowerCase().trim()));
            }

            if (brand != null && !brand.isBlank() && !brand.equalsIgnoreCase("all")) {
                predicates.add(cb.equal(cb.lower(root.get("brand")), brand.toLowerCase().trim()));
            }

            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }

            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }

            if (minRating != null && minRating > 0) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("rating"), minRating));
            }

            if (Boolean.TRUE.equals(inStock)) {
                predicates.add(cb.greaterThan(root.get("stockQuantity"), 0));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return productRepository.findAll(spec, pageable).map(this::mapToProductResponse);
    }

    public Optional<ProductResponse> getProductById(String id) {
        try {
            Long productId = Long.valueOf(id);
            return productRepository.findByIdAndActiveTrue(productId)
                    .map(this::mapToProductResponse);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public List<ProductResponse> searchProducts(String keyword) {
        return productRepository.searchProducts(keyword).stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest productRequest) {
        Product product = Product.builder()
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .price(productRequest.getPrice())
                .discount(productRequest.getDiscount() != null ? productRequest.getDiscount() : BigDecimal.ZERO)
                .brand(productRequest.getBrand())
                .category(productRequest.getCategory())
                .stockQuantity(productRequest.getStockQuantity() != null ? productRequest.getStockQuantity() : 0)
                .imageUrl(productRequest.getImageUrl())
                .active(productRequest.getActive() != null ? productRequest.getActive() : true)
                .rating(0.0)
                .reviewCount(0)
                .build();

        Product savedProduct = productRepository.save(product);
        return mapToProductResponse(savedProduct);
    }

    @Transactional
    public Optional<ProductResponse> updateProduct(Long id, ProductRequest productRequest) {
        return productRepository.findById(id).map(product -> {
            product.setName(productRequest.getName());
            product.setDescription(productRequest.getDescription());
            product.setPrice(productRequest.getPrice());
            if (productRequest.getDiscount() != null) {
                product.setDiscount(productRequest.getDiscount());
            }
            if (productRequest.getBrand() != null) {
                product.setBrand(productRequest.getBrand());
            }
            product.setCategory(productRequest.getCategory());
            if (productRequest.getStockQuantity() != null) {
                product.setStockQuantity(productRequest.getStockQuantity());
            }
            if (productRequest.getImageUrl() != null) {
                product.setImageUrl(productRequest.getImageUrl());
            }
            if (productRequest.getActive() != null) {
                product.setActive(productRequest.getActive());
            }
            Product saved = productRepository.save(product);
            return mapToProductResponse(saved);
        });
    }

    @Transactional
    public boolean deleteProduct(Long id) {
        return productRepository.findById(id).map(product -> {
            product.setActive(false);
            productRepository.save(product);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean activateProduct(Long id, boolean active) {
        return productRepository.findById(id).map(product -> {
            product.setActive(active);
            productRepository.save(product);
            return true;
        }).orElse(false);
    }

    public List<String> getAllCategories() {
        return productRepository.findDistinctCategories();
    }

    public List<String> getAllBrands() {
        return productRepository.findDistinctBrands();
    }

    public List<ProductResponse> getRecommendations(Long productId) {
        return productRepository.findById(productId).map(product -> {
            List<Product> sameCategory = productRepository.findByCategoryAndActiveTrueAndIdNot(product.getCategory(), productId);
            if (sameCategory.size() < 4 && product.getBrand() != null) {
                List<Product> sameBrand = productRepository.findByBrandAndActiveTrueAndIdNot(product.getBrand(), productId);
                Set<Product> combined = new LinkedHashSet<>(sameCategory);
                combined.addAll(sameBrand);
                return combined.stream().limit(6).map(this::mapToProductResponse).collect(Collectors.toList());
            }
            return sameCategory.stream().limit(6).map(this::mapToProductResponse).collect(Collectors.toList());
        }).orElseGet(() -> productRepository.findTop8ByActiveTrueOrderByCreatedAtDesc().stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList()));
    }

    public List<ProductResponse> getFeaturedProducts() {
        return productRepository.findTop8ByActiveTrueOrderByCreatedAtDesc().stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());
    }

    // Reviews & Ratings
    @Transactional
    public ReviewResponse addReview(Long productId, String userId, String userName, ReviewRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        if (reviewRepository.existsByProductIdAndUserId(productId, userId)) {
            throw new IllegalArgumentException("You have already reviewed this product.");
        }

        ProductReview review = ProductReview.builder()
                .productId(productId)
                .userId(userId)
                .userName(userName != null && !userName.isBlank() ? userName : "Verified Customer")
                .rating(request.getRating())
                .comment(request.getComment())
                .verifiedPurchase(isVerifiedPurchase(userId, productId))
                .build();

        ProductReview saved = reviewRepository.save(review);

        // Recalculate average rating & reviewCount
        List<ProductReview> allReviews = reviewRepository.findByProductId(productId);
        double avg = allReviews.stream().mapToInt(ProductReview::getRating).average().orElse(0.0);
        product.setRating(Math.round(avg * 10.0) / 10.0);
        product.setReviewCount(allReviews.size());
        productRepository.save(product);

        return mapToReviewResponse(saved);
    }

    /**
     * "Verified Purchase" is a label, not a gate: any failure (order-service down, 4xx, null body, ...)
     * simply means the review is stored as unverified.
     */
    //Product Service doesn't itself know whether the user purchased the product. It asks the Order Service, which owns the order/purchase information.
    private boolean isVerifiedPurchase(String userId, Long productId) {
        try {
            return Boolean.TRUE.equals(orderServiceClient.verifyPurchase(userId, String.valueOf(productId)));
        } catch (Exception e) {
            log.warn("Could not verify purchase for user {} / product {}: {}", userId, productId, e.getMessage());
            return false;
        }
    }

    public Page<ReviewResponse> getProductReviews(Long productId, Pageable pageable) {
        return reviewRepository.findByProductId(productId, pageable)
                .map(this::mapToReviewResponse);
    }

    public RatingDistributionDTO getRatingDistribution(Long productId) {
        List<ProductReview> reviews = reviewRepository.findByProductId(productId);
        Map<Integer, Long> dist = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            dist.put(i, 0L);
        }
        for (ProductReview r : reviews) {
            dist.put(r.getRating(), dist.getOrDefault(r.getRating(), 0L) + 1L);
        }

        double avg = reviews.stream().mapToInt(ProductReview::getRating).average().orElse(0.0);

        return RatingDistributionDTO.builder()
                .averageRating(Math.round(avg * 10.0) / 10.0)
                .totalReviews(reviews.size())
                .distribution(dist)
                .build();
    }

    public boolean isActiveProduct(Long productId) {
        return productId != null && productRepository.findByIdAndActiveTrue(productId).isPresent();
    }

    /**
     * Batch-loads active products for enrichment (wishlist, recently viewed). Ids of deleted/deactivated
     * products are simply absent from the returned map so callers can skip them silently.
     */
    public Map<Long, ProductResponse> getActiveProductsByIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return productRepository.findAllById(productIds).stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .collect(Collectors.toMap(Product::getId, this::mapToProductResponse, (a, b) -> a));
    }

    public ProductResponse mapToProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .discount(product.getDiscount() != null ? product.getDiscount() : BigDecimal.ZERO)
                .effectivePrice(product.getEffectivePrice())
                .brand(product.getBrand())
                .category(product.getCategory())
                .stockQuantity(product.getStockQuantity())
                .imageUrl(product.getImageUrl())
                .active(product.getActive())
                .rating(product.getRating() != null ? product.getRating() : 0.0)
                .reviewCount(product.getReviewCount() != null ? product.getReviewCount() : 0)
                .createdAt(product.getCreatedAt())
                .build();
    }

    private ReviewResponse mapToReviewResponse(ProductReview review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProductId())
                .userId(review.getUserId())
                .userName(review.getUserName())
                .rating(review.getRating())
                .comment(review.getComment())
                .verifiedPurchase(Boolean.TRUE.equals(review.getVerifiedPurchase()))
                .createdAt(review.getCreatedAt())
                .build();
    }
}
