package com.ecommerce.inventory.services;

import com.ecommerce.inventory.events.InventoryEventPublisher;
import com.ecommerce.inventory.events.InventoryReleasedEvent;
import com.ecommerce.inventory.events.OrderCreatedEvent;
import com.ecommerce.inventory.events.OrderItemDTO;
import com.ecommerce.inventory.events.ProductBackInStockEvent;
import com.ecommerce.inventory.exceptions.ResourceNotFoundException;
import com.ecommerce.inventory.models.Inventory;
import com.ecommerce.inventory.models.OrderReservation;
import com.ecommerce.inventory.models.OrderReservationItem;
import com.ecommerce.inventory.models.ProcessedEvent;
import com.ecommerce.inventory.models.ReservationStatus;
import com.ecommerce.inventory.models.StockAuditLog;
import com.ecommerce.inventory.repositories.InventoryRepository;
import com.ecommerce.inventory.repositories.OrderReservationRepository;
import com.ecommerce.inventory.repositories.ProcessedEventRepository;
import com.ecommerce.inventory.repositories.StockAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Inventory business logic: saga participation (reserve / release) and admin stock management.
 * <p>
 * Stock model: {@code availableStock = totalStock - reservedStock}.
 * Every mutation writes one {@link StockAuditLog} row where {@code previousStock} / {@code newStock} are the
 * <b>available</b> stock before / after the change (for all actions: RESERVE, RELEASE, RESTOCK, ADJUST).
 * <p>
 * All stock mutations take a row lock ({@code SELECT ... FOR UPDATE}) on the affected inventory rows, acquired in
 * ascending productId order to avoid deadlocks between concurrent orders.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private static final String SERVICE_NAME = "inventory-service";

    public static final String ACTION_RESERVE = "RESERVE";
    public static final String ACTION_RELEASE = "RELEASE";
    public static final String ACTION_RESTOCK = "RESTOCK";
    public static final String ACTION_ADJUST = "ADJUST";
    public static final String ACTION_REDUCE = "REDUCE";

    public static final String EVENT_ORDER_CREATED = "ORDER_CREATED";
    public static final String EVENT_ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String EVENT_PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String EVENT_PAYMENT_COMPLETED = "PAYMENT_COMPLETED";

    private static final String STATUS_PROCESSED = "PROCESSED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String DEFAULT_ADMIN_REF = "ADMIN";
    private static final int ERROR_MESSAGE_MAX_LENGTH = 255;

    private final InventoryRepository inventoryRepository;
    private final StockAuditLogRepository stockAuditLogRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OrderReservationRepository orderReservationRepository;
    private final InventoryEventPublisher eventPublisher;

    // ------------------------------------------------------------------------------------------------------------
    // Saga: reserve
    // ------------------------------------------------------------------------------------------------------------

    /**
     * All-or-nothing reservation of every line in the order. Idempotent on the inbound eventId.
     * The caller publishes InventoryReservedEvent / InventoryFailedEvent based on the returned outcome
     * (this method's transaction has committed by the time the caller sees the result).
     */
    @Transactional
    public ReservationResult reserveForOrder(OrderCreatedEvent event) {
        Long orderId = event.getOrderId();
        if (orderId == null) {
            log.error("Received OrderCreatedEvent without orderId (eventId={}) - ignoring", event.getEventId());
            return ReservationResult.skipped("Missing orderId");
        }

        String eventKey = resolveEventKey(event.getEventId(), EVENT_ORDER_CREATED, orderId);
        if (processedEventRepository.existsByEventId(eventKey)) {
            log.info("OrderCreatedEvent {} for order {} already processed - skipping", eventKey, orderId);
            return ReservationResult.skipped("Duplicate event " + eventKey);
        }

        Optional<OrderReservation> existingReservation = orderReservationRepository.findByOrderId(orderId);
        if (existingReservation.isPresent()) {
            String note = "Reservation already exists for order " + orderId
                    + " with status " + existingReservation.get().getStatus();
            log.warn("{} - ignoring OrderCreatedEvent {}", note, eventKey);
            recordProcessedEvent(eventKey, EVENT_ORDER_CREATED, STATUS_PROCESSED, note);
            return ReservationResult.skipped(note);
        }

        // Validate and aggregate requested quantity per product. TreeMap => deterministic (ascending) lock order.
        Map<Long, Integer> requested = new TreeMap<>();
        String validationError = aggregateRequestedQuantities(event.getItems(), requested);
        if (validationError != null) {
            return rejectReservation(eventKey, orderId, validationError);
        }

        // Phase 1: lock + verify every line. Nothing is mutated unless ALL lines can be satisfied.
        Map<Long, Inventory> lockedRows = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> line : requested.entrySet()) {
            Long productId = line.getKey();
            int quantity = line.getValue();
            Optional<Inventory> row = inventoryRepository.findByProductIdForUpdate(productId);
            if (row.isEmpty()) {
                return rejectReservation(eventKey, orderId, "Unknown product " + productId);
            }
            Inventory inventory = row.get();
            if (inventory.getAvailableStock() < quantity) {
                return rejectReservation(eventKey, orderId, String.format(
                        "Insufficient stock for product %d (requested %d, available %d)",
                        productId, quantity, inventory.getAvailableStock()));
            }
            lockedRows.put(productId, inventory);
        }

        // Phase 2: apply the reservation.
        OrderReservation reservation = OrderReservation.builder()
                .orderId(orderId)
                .userId(event.getUserId())
                .status(ReservationStatus.RESERVED)
                .build();

        for (Map.Entry<Long, Inventory> entry : lockedRows.entrySet()) {
            Long productId = entry.getKey();
            Inventory inventory = entry.getValue();
            int quantity = requested.get(productId);

            int availableBefore = inventory.getAvailableStock();
            inventory.setReservedStock(inventory.getReservedStock() + quantity);
            inventoryRepository.save(inventory);
            writeAudit(productId, ACTION_RESERVE, quantity, availableBefore, inventory.getAvailableStock(),
                    String.valueOf(orderId));

            reservation.addItem(OrderReservationItem.builder()
                    .productId(productId)
                    .quantity(quantity)
                    .build());
        }

        orderReservationRepository.save(reservation);
        recordProcessedEvent(eventKey, EVENT_ORDER_CREATED, STATUS_PROCESSED, null);
        log.info("Reserved stock for order {} ({} product line(s))", orderId, lockedRows.size());
        return ReservationResult.reserved();
    }

    private ReservationResult rejectReservation(String eventKey, Long orderId, String reason) {
        log.warn("Reservation rejected for order {}: {}", orderId, reason);
        recordProcessedEvent(eventKey, EVENT_ORDER_CREATED, STATUS_FAILED, reason);
        return ReservationResult.rejected(reason);
    }

    private String aggregateRequestedQuantities(List<OrderItemDTO> items, Map<Long, Integer> target) {
        if (items == null || items.isEmpty()) {
            return "Order contains no items";
        }
        for (OrderItemDTO item : items) {
            if (item == null) {
                return "Order contains an empty item";
            }
            Long productId = parseProductId(item.getProductId());
            if (productId == null) {
                return "Unknown product " + item.getProductId();
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                return "Invalid quantity " + item.getQuantity() + " for product " + productId;
            }
            target.merge(productId, item.getQuantity(), Integer::sum);
        }
        return null;
    }

    private Long parseProductId(String rawProductId) {
        if (!StringUtils.hasText(rawProductId)) {
            return null;
        }
        try {
            return Long.valueOf(rawProductId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Saga: release (compensation)
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Idempotent entry point for inbound compensation events (order-cancelled / payment-failed).
     * Skips events whose eventId was already processed, then delegates to {@link #releaseForOrder(Long, String)}.
     */
    @Transactional
    public void handleReleaseEvent(String inboundEventId, String eventType, Long orderId, String reason) {
        if (orderId == null) {
            log.error("Received {} event without orderId (eventId={}) - ignoring", eventType, inboundEventId);
            return;
        }
        String eventKey = resolveEventKey(inboundEventId, eventType, orderId);
        if (processedEventRepository.existsByEventId(eventKey)) {
            log.info("{} event {} for order {} already processed - skipping", eventType, eventKey, orderId);
            return;
        }
        boolean released = releaseForOrder(orderId, reason);
        recordProcessedEvent(eventKey, eventType, STATUS_PROCESSED,
                released ? null : "No active reservation for order " + orderId);
    }

    /**
     * Returns all stock reserved for the order to the available pool and publishes an InventoryReleasedEvent
     * (after commit). Safe to call repeatedly: if there is no RESERVED reservation for the order it is a no-op.
     * <p>
     * If no reservation exists at all (e.g. the cancel overtook the order-created event), a RELEASED tombstone
     * is stored so that a late-arriving OrderCreatedEvent for the same order is ignored instead of leaking stock.
     *
     * @return true if stock was actually released
     */
    @Transactional
    public boolean releaseForOrder(Long orderId, String reasonForLog) {
        if (orderId == null) {
            log.warn("releaseForOrder called with null orderId - ignoring");
            return false;
        }

        Optional<OrderReservation> found = orderReservationRepository.findByOrderId(orderId);
        if (found.isEmpty()) {
            log.info("No reservation found for order {} ({}) - storing RELEASED tombstone, nothing to release",
                    orderId, reasonForLog);
            orderReservationRepository.save(OrderReservation.builder()
                    .orderId(orderId)
                    .status(ReservationStatus.RELEASED)
                    .build());
            return false;
        }

        OrderReservation reservation = found.get();
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            log.info("Reservation for order {} is already {} ({}) - nothing to release",
                    orderId, reservation.getStatus(), reasonForLog);
            return false;
        }

        List<OrderReservationItem> items = new ArrayList<>(reservation.getItems());
        items.sort(Comparator.comparing(OrderReservationItem::getProductId));

        for (OrderReservationItem item : items) {
            Optional<Inventory> row = inventoryRepository.findByProductIdForUpdate(item.getProductId());
            if (row.isEmpty()) {
                log.warn("Inventory row for product {} no longer exists - cannot release {} unit(s) for order {}",
                        item.getProductId(), item.getQuantity(), orderId);
                continue;
            }
            Inventory inventory = row.get();
            int availableBefore = inventory.getAvailableStock();
            inventory.setReservedStock(Math.max(0, inventory.getReservedStock() - item.getQuantity()));
            inventoryRepository.save(inventory);
            writeAudit(item.getProductId(), ACTION_RELEASE, item.getQuantity(), availableBefore,
                    inventory.getAvailableStock(), String.valueOf(orderId));
            publishBackInStockIfTransitioned(inventory, availableBefore);
        }

        reservation.setStatus(ReservationStatus.RELEASED);
        orderReservationRepository.save(reservation);

        eventPublisher.publishAfterCommit(InventoryEventPublisher.INVENTORY_RELEASED_BINDING,
                InventoryReleasedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType("INVENTORY_RELEASED")
                        .orderId(orderId)
                        .userId(reservation.getUserId())
                        .reason(reasonForLog)
                        .correlationId(String.valueOf(orderId))
                        .timestamp(LocalDateTime.now())
                        .build());

        log.info("Released reserved stock for order {} ({} line(s)) - reason: {}", orderId, items.size(), reasonForLog);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Saga: confirm (payment succeeded) - converts a RESERVED reservation into a permanent deduction
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Idempotent entry point for the inbound payment-completed event. Permanently removes the reserved
     * units from totalStock (ACTION_REDUCE) so a sold item doesn't stay "reserved" forever - without this,
     * totalStock would never shrink and the inventory would eventually look fuller than it really is.
     */
    @Transactional
    public void handlePaymentCompleted(String inboundEventId, Long orderId) {
        if (orderId == null) {
            log.error("Received PaymentCompletedEvent without orderId (eventId={}) - ignoring", inboundEventId);
            return;
        }
        String eventKey = resolveEventKey(inboundEventId, EVENT_PAYMENT_COMPLETED, orderId);
        if (processedEventRepository.existsByEventId(eventKey)) {
            log.info("PaymentCompletedEvent {} for order {} already processed - skipping", eventKey, orderId);
            return;
        }

        Optional<OrderReservation> found = orderReservationRepository.findByOrderId(orderId);
        if (found.isEmpty() || found.get().getStatus() != ReservationStatus.RESERVED) {
            String note = "No active reservation to confirm for order " + orderId
                    + (found.isPresent() ? " (status " + found.get().getStatus() + ")" : "");
            log.warn(note);
            recordProcessedEvent(eventKey, EVENT_PAYMENT_COMPLETED, STATUS_PROCESSED, note);
            return;
        }

        OrderReservation reservation = found.get();
        List<OrderReservationItem> items = new ArrayList<>(reservation.getItems());
        items.sort(Comparator.comparing(OrderReservationItem::getProductId));

        for (OrderReservationItem item : items) {
            Optional<Inventory> row = inventoryRepository.findByProductIdForUpdate(item.getProductId());
            if (row.isEmpty()) {
                log.warn("Inventory row for product {} no longer exists - cannot reduce {} unit(s) for order {}",
                        item.getProductId(), item.getQuantity(), orderId);
                continue;
            }
            Inventory inventory = row.get();
            int availableBefore = inventory.getAvailableStock();
            inventory.setTotalStock(Math.max(0, inventory.getTotalStock() - item.getQuantity()));
            inventory.setReservedStock(Math.max(0, inventory.getReservedStock() - item.getQuantity()));
            inventoryRepository.save(inventory);
            writeAudit(item.getProductId(), ACTION_REDUCE, item.getQuantity(), availableBefore,
                    inventory.getAvailableStock(), String.valueOf(orderId));
        }

        reservation.setStatus(ReservationStatus.CONFIRMED);
        orderReservationRepository.save(reservation);
        recordProcessedEvent(eventKey, EVENT_PAYMENT_COMPLETED, STATUS_PROCESSED, null);
        log.info("Confirmed sale for order {} ({} line(s)) - stock permanently deducted", orderId, items.size());
    }

    // ------------------------------------------------------------------------------------------------------------
    // Admin stock management
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Adds {@code addQuantity} units to totalStock. Publishes ProductBackInStockEvent (after commit) when
     * available stock goes from 0 to a positive value.
     */
    @Transactional
    public Inventory restock(Long productId, int addQuantity, String adminRef) {
        if (addQuantity <= 0) {
            throw new IllegalArgumentException("Restock quantity must be greater than zero");
        }
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product " + productId));

        int availableBefore = inventory.getAvailableStock();
        inventory.setTotalStock(inventory.getTotalStock() + addQuantity);
        Inventory saved = inventoryRepository.save(inventory);

        writeAudit(productId, ACTION_RESTOCK, addQuantity, availableBefore, saved.getAvailableStock(),
                refOrDefault(adminRef));
        publishBackInStockIfTransitioned(saved, availableBefore);

        log.info("Restocked product {} by {} unit(s): available {} -> {}",
                productId, addQuantity, availableBefore, saved.getAvailableStock());
        return saved;
    }

    @Transactional
    public Inventory createOrUpdateInventory(Long productId, String productName, Integer totalStock,
                                             Integer lowStockThreshold) {
        return createOrUpdateInventory(productId, productName, totalStock, lowStockThreshold, null);
    }

    /**
     * Upsert used for admin seeding. Sets totalStock to the given absolute value (reservedStock is preserved).
     * Writes an ADJUST audit row whenever totalStock changes.
     */
    @Transactional
    public Inventory createOrUpdateInventory(Long productId, String productName, Integer totalStock,
                                             Integer lowStockThreshold, String adminRef) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is required");
        }
        if (totalStock == null || totalStock < 0) {
            throw new IllegalArgumentException("totalStock must be zero or greater");
        }
        if (lowStockThreshold != null && lowStockThreshold < 0) {
            throw new IllegalArgumentException("lowStockThreshold must be zero or greater");
        }
        String reference = refOrDefault(adminRef);

        Optional<Inventory> existing = inventoryRepository.findByProductIdForUpdate(productId);
        if (existing.isPresent()) {
            Inventory inventory = existing.get();
            if (totalStock < inventory.getReservedStock()) {
                throw new IllegalArgumentException(String.format(
                        "totalStock (%d) cannot be less than currently reserved stock (%d) for product %d",
                        totalStock, inventory.getReservedStock(), productId));
            }
            int availableBefore = inventory.getAvailableStock();
            int previousTotal = inventory.getTotalStock();

            if (StringUtils.hasText(productName)) {
                inventory.setProductName(productName);
            }
            inventory.setTotalStock(totalStock);
            if (lowStockThreshold != null) {
                inventory.setLowStockThreshold(lowStockThreshold);
            }
            Inventory saved = inventoryRepository.save(inventory);

            if (previousTotal != totalStock) {
                writeAudit(productId, ACTION_ADJUST, totalStock - previousTotal, availableBefore,
                        saved.getAvailableStock(), reference);
            }
            publishBackInStockIfTransitioned(saved, availableBefore);
            log.info("Updated inventory for product {}: totalStock {} -> {}", productId, previousTotal, totalStock);
            return saved;
        }

        Inventory created = Inventory.builder()
                .productId(productId)
                .productName(productName)
                .totalStock(totalStock)
                .reservedStock(0)
                .lowStockThreshold(lowStockThreshold != null ? lowStockThreshold : 10)
                .build();
        Inventory saved = inventoryRepository.save(created);
        writeAudit(productId, ACTION_ADJUST, totalStock, 0, saved.getAvailableStock(), reference);
        log.info("Created inventory for product {} with totalStock {}", productId, totalStock);
        return saved;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<Inventory> findInventory(Long productId) {
        return inventoryRepository.findByProductId(productId);
    }

    @Transactional(readOnly = true)
    public Page<Inventory> getAllInventory(Pageable pageable) {
        return inventoryRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Inventory> getLowStockItems() {
        return inventoryRepository.findLowStockItems();
    }

    @Transactional(readOnly = true)
    public List<StockAuditLog> getAuditLogs(Long productId) {
        return stockAuditLogRepository.findByProductIdOrderByTimestampDesc(productId);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------------------------

    private void publishBackInStockIfTransitioned(Inventory inventory, int availableBefore) {
        int availableAfter = inventory.getAvailableStock();
        if (availableBefore == 0 && availableAfter > 0) {
            eventPublisher.publishAfterCommit(InventoryEventPublisher.PRODUCT_BACK_IN_STOCK_BINDING,
                    ProductBackInStockEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .eventType("PRODUCT_BACK_IN_STOCK")
                            .productId(inventory.getProductId())
                            .productName(inventory.getProductName())
                            .availableStock(availableAfter)
                            .timestamp(LocalDateTime.now())
                            .build());
        }
    }

    private void writeAudit(Long productId, String action, int quantity, int previousStock, int newStock,
                            String referenceId) {
        stockAuditLogRepository.save(StockAuditLog.builder()
                .productId(productId)
                .action(action)
                .quantity(quantity)
                .previousStock(previousStock)
                .newStock(newStock)
                .referenceId(referenceId)
                .timestamp(LocalDateTime.now())
                .build());
    }

    private void recordProcessedEvent(String eventKey, String eventType, String status, String message) {
        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(eventKey)
                .eventType(eventType)
                .service(SERVICE_NAME)
                .status(status)
                .errorMessage(truncate(message))
                .timestamp(LocalDateTime.now())
                .build());
    }

    /**
     * Uses the producer's eventId when present; otherwise falls back to a deterministic key so that
     * idempotency still holds for events published without an eventId.
     */
    private String resolveEventKey(String eventId, String eventType, Long orderId) {
        return StringUtils.hasText(eventId) ? eventId : eventType + "-" + orderId;
    }

    private String refOrDefault(String adminRef) {
        return StringUtils.hasText(adminRef) ? adminRef : DEFAULT_ADMIN_REF;
    }

    private String truncate(String message) {
        if (message == null || message.length() <= ERROR_MESSAGE_MAX_LENGTH) {
            return message;
        }
        return message.substring(0, ERROR_MESSAGE_MAX_LENGTH);
    }
}
