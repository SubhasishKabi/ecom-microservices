package com.ecommerce.product;

import com.ecommerce.product.clients.OrderServiceClient;
import com.ecommerce.product.dtos.ProductRequest;
import com.ecommerce.product.dtos.ProductResponse;
import com.ecommerce.product.dtos.ReviewRequest;
import com.ecommerce.product.dtos.ReviewResponse;
import com.ecommerce.product.models.Product;
import com.ecommerce.product.models.ProductReview;
import com.ecommerce.product.repositories.ProductRepository;
import com.ecommerce.product.repositories.ReviewRepository;
import com.ecommerce.product.services.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private OrderServiceClient orderServiceClient;

    @InjectMocks
    private ProductService productService;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = Product.builder()
                .id(1L)
                .name("Wireless Mouse")
                .description("Ergonomic wireless mouse")
                .price(new BigDecimal("29.99"))
                .discount(new BigDecimal("10.00"))
                .brand("Logitech")
                .category("Electronics")
                .stockQuantity(50)
                .active(true)
                .rating(4.5)
                .reviewCount(10)
                .build();
    }

    @Test
    void testCreateProduct() {
        ProductRequest request = ProductRequest.builder()
                .name("Wireless Mouse")
                .description("Ergonomic wireless mouse")
                .price(new BigDecimal("29.99"))
                .category("Electronics")
                .brand("Logitech")
                .stockQuantity(50)
                .build();

        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        ProductResponse response = productService.createProduct(request);

        assertNotNull(response);
        assertEquals("Wireless Mouse", response.getName());
        assertEquals(new BigDecimal("26.99"), response.getEffectivePrice());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void testGetProductById() {
        when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(sampleProduct));

        Optional<ProductResponse> response = productService.getProductById("1");

        assertTrue(response.isPresent());
        assertEquals("Wireless Mouse", response.get().getName());
    }

    @Test
    void testAddReviewSuccess() {
        ReviewRequest request = ReviewRequest.builder()
                .rating(5)
                .comment("Excellent mouse!")
                .build();

        ProductReview savedReview = ProductReview.builder()
                .id(10L)
                .productId(1L)
                .userId("user-123")
                .userName("John")
                .rating(5)
                .comment("Excellent mouse!")
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(reviewRepository.existsByProductIdAndUserId(1L, "user-123")).thenReturn(false);
        when(reviewRepository.save(any(ProductReview.class))).thenReturn(savedReview);
        when(reviewRepository.findByProductId(1L)).thenReturn(List.of(savedReview));

        ReviewResponse response = productService.addReview(1L, "user-123", "John", request);

        assertNotNull(response);
        assertEquals(5, response.getRating());
        assertEquals("Excellent mouse!", response.getComment());
        verify(productRepository, times(1)).save(sampleProduct);
    }
}
