package com.ecommerce.inventory.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Thin wrapper around {@link StreamBridge} that publishes to the explicitly configured output bindings
 * (see config-server {@code inventory-service.yml}).
 * <p>
 * {@link #publishAfterCommit} defers the send until the surrounding DB transaction has committed, so we never
 * announce a stock change that was subsequently rolled back.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventPublisher {

    public static final String INVENTORY_RESERVED_BINDING = "inventoryReserved-out-0";
    public static final String INVENTORY_FAILED_BINDING = "inventoryFailed-out-0";
    public static final String INVENTORY_RELEASED_BINDING = "inventoryReleased-out-0";
    public static final String PRODUCT_BACK_IN_STOCK_BINDING = "productBackInStock-out-0";

    private final StreamBridge streamBridge;

    public void publish(String bindingName, Object payload) {
        boolean sent = streamBridge.send(bindingName, payload);
        if (sent) {
            log.info("Published {} to binding {}", payload.getClass().getSimpleName(), bindingName);
        } else {
            log.error("Failed to publish {} to binding {}: {}", payload.getClass().getSimpleName(), bindingName, payload);
        }
    }

    public void publishAfterCommit(String bindingName, Object payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(bindingName, payload);
                }
            });
        } else {
            publish(bindingName, payload);
        }
    }
}
