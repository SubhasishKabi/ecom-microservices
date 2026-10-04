package com.ecommerce.order.services;

import com.ecommerce.order.clients.PaymentServiceClient;
import com.ecommerce.order.clients.ProductServiceClient;
import com.ecommerce.order.clients.UserServiceClient;
import com.ecommerce.order.dtos.*;
import com.ecommerce.order.models.*;
import com.ecommerce.order.repositories.OrderRepository;
import com.ecommerce.order.repositories.OrderReturnRepository;
import com.ecommerce.order.repositories.ProcessedEventRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final CartService cartService;
    private final OrderRepository orderRepository;
    private final OrderReturnRepository orderReturnRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final StreamBridge streamBridge;
    private final ProductServiceClient productServiceClient;
    private final UserServiceClient userServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    @Value("${order.tax-rate:0.08}")
    private BigDecimal taxRate;

    @Value("${order.free-shipping-threshold:100.00}")
    private BigDecimal freeShippingThreshold;

    @Value("${order.shipping-fee:9.99}")
    private BigDecimal shippingFeeAmount;

    @Transactional
    public OrderResponse checkout(String userId, CheckoutRequest request) {
        List<CartItem> cartItems = cartService.getCart(userId);
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        UserResponse user = userServiceClient.getUserDetails(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        AddressDTO shippingAddress = resolveAddress(user, request != null ? request.getAddressId() : null);
        if (shippingAddress == null) {
            throw new IllegalArgumentException("No shipping address available. Please add an address before checking out.");
        }

        // Server-side re-validation of price & stock against the live product catalog - never trust stale cart prices
        List<OrderItem> orderItems = cartItems.stream().map(item -> {
            ProductResponse product = productServiceClient.getProductDetails(item.getProductId());
            if (product == null || !Boolean.TRUE.equals(product.getActive())) {
                throw new IllegalArgumentException("Product " + item.getProductId() + " is no longer available");
            }
            if (product.getStockQuantity() == null || product.getStockQuantity() < item.getQuantity()) {
                throw new IllegalArgumentException("Insufficient stock for product " + item.getProductId());
            }
            BigDecimal livePrice = product.getEffectivePrice() != null ? product.getEffectivePrice() : product.getPrice();
            return new OrderItem(null, item.getProductId(), item.getQuantity(), livePrice, null);
        }).collect(Collectors.toList());

        BigDecimal subtotal = orderItems.stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal shippingFee = subtotal.compareTo(freeShippingThreshold) >= 0 ? BigDecimal.ZERO : shippingFeeAmount;
        BigDecimal totalAmount = subtotal.add(tax).add(shippingFee);

        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);
        order.setSubtotal(subtotal);
        order.setTax(tax);
        order.setShippingFee(shippingFee);
        order.setTotalAmount(totalAmount);
        order.setShippingStreet(shippingAddress.getStreet());
        order.setShippingCity(shippingAddress.getCity());
        order.setShippingState(shippingAddress.getState());
        order.setShippingZipcode(shippingAddress.getZipcode());
        order.setShippingCountry(shippingAddress.getCountry());

        orderItems.forEach(i -> i.setOrder(order));
        order.setItems(orderItems);

        Order savedOrder = orderRepository.save(order);
        cartService.clearCart(userId);

        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                savedOrder.getId(),
                savedOrder.getUserId(),
                savedOrder.getStatus(),
                mapToOrderItemDTOs(savedOrder.getItems()),
                savedOrder.getTotalAmount(),
                savedOrder.getCreatedAt()
        );
        streamBridge.send("orderCreated-out-0", event);
        log.info("Published OrderCreatedEvent for order {}", savedOrder.getId());

        return mapToOrderResponse(savedOrder);
    }

    private AddressDTO resolveAddress(UserResponse user, Long addressId) {
        if (user.getAddresses() == null) return user.getDefaultAddress();
        if (addressId != null) {
            return user.getAddresses().stream().filter(a -> addressId.equals(a.getId())).findFirst()
                    .orElse(user.getDefaultAddress());
        }
        return user.getDefaultAddress();
    }

    public Page<OrderResponse> getOrdersForUser(String userId, Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(this::mapToOrderResponse);
    }

    public Optional<OrderResponse> getOrder(String userId, Long orderId, boolean isAdmin) {
        Optional<Order> order = isAdmin ? orderRepository.findById(orderId) : orderRepository.findByIdAndUserId(orderId, userId);
        return order.map(this::mapToOrderResponse);
    }

    public Page<OrderResponse> getAllOrdersAdmin(OrderStatus status, Pageable pageable) {
        if (status != null) {
            return orderRepository.findByStatus(status, pageable).map(this::mapToOrderResponse);
        }
        return orderRepository.findAll(pageable).map(this::mapToOrderResponse);
    }

    public boolean verifyPurchase(String userId, String productId) {
        return orderRepository.existsVerifiedPurchase(userId, productId);
    }

    @Transactional
    public OrderResponse cancelOrder(String userId, Long orderId, String reason, boolean isAdmin) {
        Order order = (isAdmin ? orderRepository.findById(orderId) : orderRepository.findByIdAndUserId(orderId, userId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (!List.of(OrderStatus.PENDING, OrderStatus.PROCESSING, OrderStatus.CONFIRMED).contains(order.getStatus())) {
            throw new IllegalStateException("Order in status " + order.getStatus() + " cannot be cancelled. Use a return request instead.");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason != null ? reason : "Cancelled by " + (isAdmin ? "admin" : "customer"));
        Order saved = orderRepository.save(order);

        OrderCancelledEvent event = new OrderCancelledEvent(
                UUID.randomUUID().toString(), saved.getId(), saved.getUserId(), saved.getCancellationReason(), LocalDateTime.now());
        streamBridge.send("orderCancelled-out-0", event);

        return mapToOrderResponse(saved);
    }

    @Transactional
    public OrderResponse updateStatusAdmin(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (newStatus == OrderStatus.SHIPPED && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Only a CONFIRMED order can be marked SHIPPED");
        }
        if (newStatus == OrderStatus.DELIVERED && order.getStatus() != OrderStatus.SHIPPED) {
            throw new IllegalStateException("Only a SHIPPED order can be marked DELIVERED");
        }

        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);

        if (newStatus == OrderStatus.SHIPPED) {
            streamBridge.send("orderShipped-out-0",
                    new OrderShippedEvent(UUID.randomUUID().toString(), saved.getId(), saved.getUserId(), LocalDateTime.now()));
        } else if (newStatus == OrderStatus.DELIVERED) {
            streamBridge.send("orderDelivered-out-0",
                    new OrderDeliveredEvent(UUID.randomUUID().toString(), saved.getId(), saved.getUserId(), LocalDateTime.now()));
        }

        return mapToOrderResponse(saved);
    }

    // ---- Returns ----

    @Transactional
    public ReturnResponse requestReturn(String userId, Long orderId, String reason) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException("Only delivered orders are eligible for return");
        }
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setOrderId(orderId);
        orderReturn.setUserId(userId);
        orderReturn.setReason(reason);
        orderReturn.setStatus(ReturnStatus.REQUESTED);
        return mapToReturnResponse(orderReturnRepository.save(orderReturn));
    }

    public Page<ReturnResponse> getReturnsForUser(String userId, Pageable pageable) {
        return orderReturnRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(this::mapToReturnResponse);
    }

    public Page<ReturnResponse> getAllReturnsAdmin(Pageable pageable) {
        return orderReturnRepository.findAll(pageable).map(this::mapToReturnResponse);
    }

    @Transactional
    @CircuitBreaker(name = "paymentService", fallbackMethod = "decideReturnFallback")
    public ReturnResponse decideReturnAdmin(Long returnId, boolean approve, String note, String authorizationHeader) {
        OrderReturn orderReturn = orderReturnRepository.findById(returnId)
                .orElseThrow(() -> new IllegalArgumentException("Return request not found: " + returnId));
        if (orderReturn.getStatus() != ReturnStatus.REQUESTED) {
            throw new IllegalStateException("This return request has already been decided");
        }

        if (!approve) {
            orderReturn.setStatus(ReturnStatus.REJECTED);
            orderReturn.setAdminNote(note);
            return mapToReturnResponse(orderReturnRepository.save(orderReturn));
        }

        orderReturn.setStatus(ReturnStatus.APPROVED);
        orderReturn.setAdminNote(note);
        orderReturnRepository.save(orderReturn);

        paymentServiceClient.refund(orderReturn.getOrderId(), authorizationHeader,
                Map.of("reason", "Return approved: " + (note != null ? note : "")));

        orderReturn.setStatus(ReturnStatus.REFUNDED);
        return mapToReturnResponse(orderReturnRepository.save(orderReturn));
    }

    public ReturnResponse decideReturnFallback(Long returnId, boolean approve, String note, String authorizationHeader, Exception ex) {
        log.error("payment-service unavailable while processing return {}: {}", returnId, ex.getMessage());
        // The surrounding @Transactional method rolls back on this exception, so the APPROVED
        // write made earlier in the same call is undone too - the return is left REQUESTED, not APPROVED.
        throw new IllegalStateException("Unable to process refund right now, payment-service is unavailable. The return request remains in REQUESTED status - please retry shortly.");
    }

    // ---- Saga event consumers (idempotent) ----

    @Transactional
    public void onInventoryReserved(InventoryReservedEvent event) {
        if (alreadyProcessed(event.getEventId())) return;
        orderRepository.findById(event.getOrderId()).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.PROCESSING);
                orderRepository.save(order);
            }
        });
        markProcessed(event.getEventId(), "INVENTORY_RESERVED");
    }

    @Transactional
    public void onInventoryFailed(InventoryFailedEvent event) {
        if (alreadyProcessed(event.getEventId())) return;
        orderRepository.findById(event.getOrderId()).ifPresent(order -> {
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancellationReason("Inventory reservation failed: " + event.getReason());
            orderRepository.save(order);
        });
        markProcessed(event.getEventId(), "INVENTORY_FAILED");
    }

    @Transactional
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        if (alreadyProcessed(event.getEventId())) return;
        orderRepository.findById(event.getOrderId()).ifPresent(order -> {
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
        });
        markProcessed(event.getEventId(), "PAYMENT_COMPLETED");
    }

    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event) {
        if (alreadyProcessed(event.getEventId())) return;
        orderRepository.findById(event.getOrderId()).ifPresent(order -> {
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancellationReason("Payment failed: " + event.getReason());
            orderRepository.save(order);
        });
        markProcessed(event.getEventId(), "PAYMENT_FAILED");
    }

    private boolean alreadyProcessed(String eventId) {
        if (eventId == null) return false;
        return processedEventRepository.existsByEventId(eventId);
    }

    private void markProcessed(String eventId, String eventType) {
        if (eventId == null) return;
        processedEventRepository.save(new ProcessedEvent(eventId, eventType, "PROCESSED", LocalDateTime.now()));
    }

    // ---- Mapping helpers ----

    private List<OrderItemDTO> mapToOrderItemDTOs(List<OrderItem> items) {
        return items.stream()
                .map(item -> new OrderItemDTO(
                        item.getId(),
                        item.getProductId(),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getPrice().multiply(new BigDecimal(item.getQuantity()))
                )).collect(Collectors.toList());
    }

    private OrderResponse mapToOrderResponse(Order order) {
        AddressDTO address = new AddressDTO();
        address.setStreet(order.getShippingStreet());
        address.setCity(order.getShippingCity());
        address.setState(order.getShippingState());
        address.setZipcode(order.getShippingZipcode());
        address.setCountry(order.getShippingCountry());

        return OrderResponse.builder()
                .id(order.getId())
                .subtotal(order.getSubtotal())
                .tax(order.getTax())
                .shippingFee(order.getShippingFee())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .cancellationReason(order.getCancellationReason())
                .shippingAddress(address)
                .items(mapToOrderItemDTOs(order.getItems()))
                .createdAt(order.getCreatedAt())
                .build();
    }

    private ReturnResponse mapToReturnResponse(OrderReturn r) {
        return new ReturnResponse(r.getId(), r.getOrderId(), r.getUserId(), r.getReason(), r.getAdminNote(), r.getStatus(), r.getCreatedAt());
    }
}
