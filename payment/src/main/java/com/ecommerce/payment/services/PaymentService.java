package com.ecommerce.payment.services;

import com.ecommerce.payment.events.InventoryReservedEvent;
import com.ecommerce.payment.events.OrderCancelledEvent;
import com.ecommerce.payment.events.PaymentCompletedEvent;
import com.ecommerce.payment.events.PaymentFailedEvent;
import com.ecommerce.payment.events.RefundCompletedEvent;
import com.ecommerce.payment.exceptions.PaymentNotFoundException;
import com.ecommerce.payment.models.Payment;
import com.ecommerce.payment.models.PaymentStatus;
import com.ecommerce.payment.models.ProcessedEvent;
import com.ecommerce.payment.repositories.PaymentRepository;
import com.ecommerce.payment.repositories.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final String SERVICE_NAME = "payment-service";

    private static final String PAYMENT_COMPLETED_BINDING = "paymentCompleted-out-0";
    private static final String PAYMENT_FAILED_BINDING = "paymentFailed-out-0";
    private static final String REFUND_COMPLETED_BINDING = "refundCompleted-out-0";

    private static final String MOCK_DECLINE_REASON = "Mock payment gateway declined the transaction";
    private static final String DEFAULT_CANCEL_REFUND_REASON = "Order cancelled";
    private static final String DEFAULT_ADMIN_REFUND_REASON = "Refund approved by admin";
    private static final int MAX_REASON_LENGTH = 255;

    private final PaymentRepository paymentRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final StreamBridge streamBridge;

    @Value("${payment.mock.success-rate:0.9}")
    private double successRate;

    // ---------------------------------------------------------------------------------------------
    // Saga step: inventory-reserved -> mock charge -> payment-completed | payment-failed
    // ---------------------------------------------------------------------------------------------

    @Transactional
    public void processPayment(InventoryReservedEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.error("Received invalid InventoryReservedEvent without orderId, ignoring: {}", event);
            return;
        }

        String eventId = event.getEventId();
        Long orderId = event.getOrderId();
        log.info("Received InventoryReservedEvent {} for orderId={} amount={}", eventId, orderId, event.getTotalAmount());

        if (eventId != null && processedEventRepository.existsByEventId(eventId)) {
            log.info("InventoryReservedEvent {} already processed, skipping (orderId={})", eventId, orderId);
            return;
        }

        Optional<Payment> existing = paymentRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            log.info("Payment {} already exists for orderId={} with status {}, skipping duplicate InventoryReservedEvent {}",
                    existing.get().getId(), orderId, existing.get().getStatus(), eventId);
            return;
        }

        BigDecimal amount = event.getTotalAmount();
        boolean validAmount = amount != null && amount.signum() >= 0;

        // Insert + flush the PENDING record first so the unique(orderId) constraint is hit before anything is published
        Payment payment = paymentRepository.saveAndFlush(Payment.builder()
                .orderId(orderId)
                .userId(event.getUserId())
                .amount(validAmount ? amount : BigDecimal.ZERO)
                .status(PaymentStatus.PENDING)
                .idempotencyKey(eventId)
                .build());

        boolean success = validAmount && ThreadLocalRandom.current().nextDouble() < successRate;
        String failureReason = null;

        if (success) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment = paymentRepository.save(payment);

            PaymentCompletedEvent completedEvent = PaymentCompletedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .orderId(payment.getOrderId())
                    .userId(payment.getUserId())
                    .paymentId(String.valueOf(payment.getId()))
                    .amount(payment.getAmount())
                    .timestamp(LocalDateTime.now())
                    .build();
            publish(PAYMENT_COMPLETED_BINDING, completedEvent);
            log.info("Payment {} SUCCESS for orderId={}, published PaymentCompletedEvent {}",
                    payment.getId(), orderId, completedEvent.getEventId());
        } else {
            failureReason = validAmount ? MOCK_DECLINE_REASON : "Invalid payment amount: " + amount;
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(failureReason);
            payment = paymentRepository.save(payment);

            PaymentFailedEvent failedEvent = PaymentFailedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .orderId(payment.getOrderId())
                    .userId(payment.getUserId())
                    .reason(failureReason)
                    .amount(payment.getAmount())
                    .timestamp(LocalDateTime.now())
                    .build();
            publish(PAYMENT_FAILED_BINDING, failedEvent);
            log.warn("Payment {} FAILED for orderId={} ({}), published PaymentFailedEvent {}",
                    payment.getId(), orderId, failureReason, failedEvent.getEventId());
        }

        recordProcessedEvent(eventId,
                event.getEventType() != null ? event.getEventType() : "INVENTORY_RESERVED",
                failureReason);
    }

    // ---------------------------------------------------------------------------------------------
    // order-cancelled -> refund if a completed payment exists
    // ---------------------------------------------------------------------------------------------

    @Transactional
    public void handleOrderCancelled(OrderCancelledEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.error("Received invalid OrderCancelledEvent without orderId, ignoring: {}", event);
            return;
        }

        String eventId = event.getEventId();
        Long orderId = event.getOrderId();
        log.info("Received OrderCancelledEvent {} for orderId={}", eventId, orderId);

        if (eventId != null && processedEventRepository.existsByEventId(eventId)) {
            log.info("OrderCancelledEvent {} already processed, skipping (orderId={})", eventId, orderId);
            return;
        }

        Optional<Payment> paymentOpt = paymentRepository.findByOrderIdForUpdate(orderId);
        if (paymentOpt.isEmpty()) {
            log.info("No payment found for cancelled orderId={}, nothing to refund", orderId);
        } else if (paymentOpt.get().getStatus() != PaymentStatus.SUCCESS) {
            log.info("Payment {} for cancelled orderId={} is {}, no refund required",
                    paymentOpt.get().getId(), orderId, paymentOpt.get().getStatus());
        } else {
            String reason = hasText(event.getReason()) ? event.getReason() : DEFAULT_CANCEL_REFUND_REASON;
            refund(paymentOpt.get(), reason);
        }

        recordProcessedEvent(eventId, "ORDER_CANCELLED", null);
    }

    // ---------------------------------------------------------------------------------------------
    // Synchronous admin refund (post-delivery returns) - idempotent
    // ---------------------------------------------------------------------------------------------

    @Transactional
    public Payment refundByOrderId(Long orderId, String reason) {
        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new PaymentNotFoundException("No payment found for order " + orderId));

        return switch (payment.getStatus()) {
            case REFUNDED -> {
                log.info("Payment {} for orderId={} already REFUNDED at {}, returning existing refund",
                        payment.getId(), orderId, payment.getRefundedAt());
                yield payment;
            }
            case SUCCESS -> refund(payment, hasText(reason) ? reason : DEFAULT_ADMIN_REFUND_REASON);
            case PENDING, FAILED -> throw new IllegalStateException("Cannot refund a payment that was never completed");
        };
    }

    // ---------------------------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<Payment> getPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public Page<Payment> getAllPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private Payment refund(Payment payment, String reason) {
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(LocalDateTime.now());
        payment.setRefundReason(truncate(reason));
        Payment saved = paymentRepository.save(payment);

        RefundCompletedEvent refundEvent = RefundCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(saved.getOrderId())
                .userId(saved.getUserId())
                .refundId("REF-" + saved.getId())
                .amount(saved.getAmount())
                .timestamp(LocalDateTime.now())
                .build();
        publish(REFUND_COMPLETED_BINDING, refundEvent);
        log.info("Payment {} for orderId={} REFUNDED ({}), published RefundCompletedEvent {}",
                saved.getId(), saved.getOrderId(), saved.getRefundReason(), refundEvent.getEventId());
        return saved;
    }

    /**
     * Publishes via StreamBridge; a failed hand-off throws so the surrounding transaction rolls back
     * (and, for Kafka-triggered flows, the inbound message is redelivered).
     */
    private void publish(String bindingName, Object payload) {
        boolean sent = streamBridge.send(bindingName, payload);
        if (!sent) {
            throw new IllegalStateException("Failed to publish event to binding " + bindingName);
        }
    }

    private void recordProcessedEvent(String eventId, String eventType, String errorMessage) {
        if (eventId == null) {
            log.warn("Inbound {} event has no eventId; skipping processed-event record (orderId-level idempotency still applies)",
                    eventType);
            return;
        }
        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .service(SERVICE_NAME)
                .status("PROCESSED")
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .build());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String truncate(String value) {
        return value != null && value.length() > MAX_REASON_LENGTH ? value.substring(0, MAX_REASON_LENGTH) : value;
    }
}
