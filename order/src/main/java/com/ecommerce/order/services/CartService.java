package com.ecommerce.order.services;

import com.ecommerce.order.clients.ProductServiceClient;
import com.ecommerce.order.clients.UserServiceClient;
import com.ecommerce.order.dtos.ProductResponse;
import com.ecommerce.order.dtos.UserResponse;
import com.ecommerce.order.repositories.CartItemRepository;
import com.ecommerce.order.dtos.CartItemRequest;
import com.ecommerce.order.models.CartItem;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CartService {
    private final CartItemRepository cartItemRepository;
    private final ProductServiceClient productServiceClient;
    private final UserServiceClient userServiceClient;


    @CircuitBreaker(name = "productService", fallbackMethod = "addToCartFallBack")
    @Retry(name = "productRetry", fallbackMethod = "addToCartFallBack")
    public boolean addToCart(String userId, CartItemRequest request) {
        // Look for product - always trust the live product price/stock, never the client-supplied value
        ProductResponse productResponse = productServiceClient.getProductDetails(request.getProductId());
        if (productResponse == null || !Boolean.TRUE.equals(productResponse.getActive())
                || productResponse.getStockQuantity() < request.getQuantity())
            return false;

        UserResponse userResponse = userServiceClient.getUserDetails(userId);
        if (userResponse == null)
            return false;

        BigDecimal currentPrice = productResponse.getEffectivePrice() != null
                ? productResponse.getEffectivePrice() : productResponse.getPrice();

        CartItem existingCartItem = cartItemRepository.findByUserIdAndProductId(userId, request.getProductId());
        if (existingCartItem != null) {
            // Update the quantity and always refresh to the current live price
            existingCartItem.setQuantity(existingCartItem.getQuantity() + request.getQuantity());
            existingCartItem.setPrice(currentPrice);
            cartItemRepository.save(existingCartItem);
        } else {
            // Create new cart item
           CartItem cartItem = new CartItem();
           cartItem.setUserId(userId);
           cartItem.setProductId(request.getProductId());
           cartItem.setQuantity(request.getQuantity());
           cartItem.setPrice(currentPrice);
           cartItemRepository.save(cartItem);
        }
        return true;
    }

    public boolean addToCartFallBack(String userId,
                                     CartItemRequest request,
                                     Exception exception) {
        exception.printStackTrace();
        return false;
    }

    public boolean updateQuantity(String userId, String productId, int quantity) {
        if (quantity <= 0) {
            return deleteItemFromCart(userId, productId);
        }
        CartItem existingCartItem = cartItemRepository.findByUserIdAndProductId(userId, productId);
        if (existingCartItem == null) {
            return false;
        }
        ProductResponse productResponse = productServiceClient.getProductDetails(productId);
        if (productResponse == null || !Boolean.TRUE.equals(productResponse.getActive())
                || productResponse.getStockQuantity() < quantity) {
            return false;
        }
        BigDecimal currentPrice = productResponse.getEffectivePrice() != null
                ? productResponse.getEffectivePrice() : productResponse.getPrice();
        existingCartItem.setQuantity(quantity);
        existingCartItem.setPrice(currentPrice);
        cartItemRepository.save(existingCartItem);
        return true;
    }

    public boolean deleteItemFromCart(String userId, String productId) {
        CartItem cartItem = cartItemRepository.findByUserIdAndProductId(userId, productId);

        if (cartItem != null){
            cartItemRepository.delete(cartItem);
            return true;
        }
        return false;
    }

    public List<CartItem> getCart(String userId) {
        return cartItemRepository.findByUserId(userId);
    }

    public void clearCart(String userId) {
        cartItemRepository.deleteByUserId(userId);
    }
}
