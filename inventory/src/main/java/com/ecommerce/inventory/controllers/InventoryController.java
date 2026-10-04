package com.ecommerce.inventory.controllers;

import com.ecommerce.inventory.dtos.InventoryRequest;
import com.ecommerce.inventory.dtos.InventoryResponse;
import com.ecommerce.inventory.dtos.RestockRequest;
import com.ecommerce.inventory.models.StockAuditLog;
import com.ecommerce.inventory.services.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * Inventory REST API. Access rules are enforced in {@code SecurityConfig}:
 * {@code GET /{productId}} is public (used by order-service at checkout); everything else requires ROLE_ADMIN.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/inventory", "/api/inventory"})
@Tag(name = "Inventory", description = "Stock lookup and admin stock management APIs")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId:\\d+}")
    @Operation(summary = "Get current stock snapshot for a product (public)")
    public ResponseEntity<InventoryResponse> getStock(@PathVariable Long productId) {
        return inventoryService.findInventory(productId)
                .map(InventoryResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "List all inventory rows with pagination (Admin)")
    public ResponseEntity<Page<InventoryResponse>> getAllInventory(
            @PageableDefault(size = 20, sort = "productId", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(inventoryService.getAllInventory(pageable).map(InventoryResponse::from));
    }

    @GetMapping("/low-stock")
    @Operation(summary = "List items whose available stock is at or below their low-stock threshold (Admin)")
    public ResponseEntity<List<InventoryResponse>> getLowStockItems() {
        return ResponseEntity.ok(inventoryService.getLowStockItems().stream()
                .map(InventoryResponse::from)
                .toList());
    }

    @PostMapping
    @Operation(summary = "Create or update the inventory record for a product (Admin)")
    public ResponseEntity<InventoryResponse> createOrUpdateInventory(@Valid @RequestBody InventoryRequest request,
                                                                     Principal principal) {
        return ResponseEntity.ok(InventoryResponse.from(inventoryService.createOrUpdateInventory(
                request.getProductId(),
                request.getProductName(),
                request.getTotalStock(),
                request.getLowStockThreshold(),
                principalName(principal))));
    }

    @PostMapping("/{productId:\\d+}/restock")
    @Operation(summary = "Add stock to a product (Admin)")
    public ResponseEntity<InventoryResponse> restock(@PathVariable Long productId,
                                                     @Valid @RequestBody RestockRequest request,
                                                     Principal principal) {
        String reference = StringUtils.hasText(request.getReference())
                ? request.getReference()
                : principalName(principal);
        return ResponseEntity.ok(InventoryResponse.from(
                inventoryService.restock(productId, request.getQuantity(), reference)));
    }

    @GetMapping("/{productId:\\d+}/audit-logs")
    @Operation(summary = "Get stock audit trail for a product, newest first (Admin)")
    public ResponseEntity<List<StockAuditLog>> getAuditLogs(@PathVariable Long productId) {
        return ResponseEntity.ok(inventoryService.getAuditLogs(productId));
    }

    private String principalName(Principal principal) {
        return principal != null && StringUtils.hasText(principal.getName()) ? principal.getName() : null;
    }
}
