package com.ecommerce.product.controllers;

import com.ecommerce.product.dtos.*;
import com.ecommerce.product.security.CurrentUserResolver;
import com.ecommerce.product.services.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/products", "/api/products"})
@Tag(name = "Product Catalog", description = "Product browsing, search, reviews, and admin management APIs")
public class ProductController {

    private final ProductService productService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/simulate")
    @Operation(summary = "Simulate service failure for testing Resilience4j")
    public ResponseEntity<String> simulateFailure(@RequestParam(defaultValue = "false") boolean fail) {
        if (fail) {
            throw new RuntimeException("Simulated Failure For Testing Resilience4j");
        }
        return ResponseEntity.ok("Product Service is OK");
    }

    @GetMapping
    @Operation(summary = "Get filtered and sorted products with server-side pagination")
    public ResponseEntity<Page<ProductResponse>> getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        String[] sortParams = sort.split(",");
        String sortField = sortParams[0];
        Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        // Map common frontend sort terms to entity fields
        if (sortField.equalsIgnoreCase("price_asc")) {
            sortField = "price";
            direction = Sort.Direction.ASC;
        } else if (sortField.equalsIgnoreCase("price_desc")) {
            sortField = "price";
            direction = Sort.Direction.DESC;
        } else if (sortField.equalsIgnoreCase("rating")) {
            sortField = "rating";
            direction = Sort.Direction.DESC;
        } else if (sortField.equalsIgnoreCase("newest")) {
            sortField = "createdAt";
            direction = Sort.Direction.DESC;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        return ResponseEntity.ok(productService.filterProducts(
                keyword, category, brand, minPrice, maxPrice, minRating, inStock, pageable));
    }

    @GetMapping("/all")
    @Operation(summary = "Get all active products without pagination")
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @GetMapping("/featured")
    @Operation(summary = "Get featured and latest products for homepage")
    public ResponseEntity<List<ProductResponse>> getFeaturedProducts() {
        return ResponseEntity.ok(productService.getFeaturedProducts());
    }

    @GetMapping("/categories")
    @Operation(summary = "Get distinct categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(productService.getAllCategories());
    }

    @GetMapping("/brands")
    @Operation(summary = "Get distinct brands")
    public ResponseEntity<List<String>> getBrands() {
        return ResponseEntity.ok(productService.getAllBrands());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable String id) {
        return productService.getProductById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/recommendations")
    @Operation(summary = "Get rule-based product recommendations")
    public ResponseEntity<List<ProductResponse>> getRecommendations(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getRecommendations(id));
    }

    @PostMapping
    @Operation(summary = "Create a new product (Admin)")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest productRequest) {
        return new ResponseEntity<>(productService.createProduct(productRequest), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing product (Admin)")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest productRequest) {
        return productService.updateProduct(id, productRequest)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete or deactivate a product (Admin)")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        boolean deleted = productService.deleteProduct(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Activate or deactivate product (Admin)")
    public ResponseEntity<String> toggleProductStatus(@PathVariable Long id, @RequestParam boolean active) {
        boolean updated = productService.activateProduct(id, active);
        if (updated) {
            return ResponseEntity.ok("Product status updated to: " + (active ? "ACTIVE" : "INACTIVE"));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/search")
    @Operation(summary = "Keyword search for products")
    public ResponseEntity<List<ProductResponse>> searchProducts(@RequestParam String keyword) {
        return ResponseEntity.ok(productService.searchProducts(keyword));
    }

    // Reviews Endpoints
    @GetMapping("/{id}/reviews")
    @Operation(summary = "Get paged reviews for product")
    public ResponseEntity<Page<ReviewResponse>> getProductReviews(
            @PathVariable Long id,
            @PageableDefault(size = 5, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(productService.getProductReviews(id, pageable));
    }

    @GetMapping("/{id}/reviews/distribution")
    @Operation(summary = "Get review distribution and average rating for product")
    public ResponseEntity<RatingDistributionDTO> getRatingDistribution(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getRatingDistribution(id));
    }

    @PostMapping("/{id}/reviews")
    @Operation(summary = "Submit a product review (logged-in users)")
    public ResponseEntity<ReviewResponse> addReview(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-User-Name", defaultValue = "Customer") String userName,
            @Valid @RequestBody ReviewRequest request) {

        String userId = currentUserResolver.resolveUserId(userIdHeader, authHeader);
        return new ResponseEntity<>(productService.addReview(id, userId, userName, request), HttpStatus.CREATED);
    }
}
