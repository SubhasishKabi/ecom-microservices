package com.ecommerce.inventory.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String action; // RESERVE, RELEASE, REDUCE, RESTOCK, ADJUST

    private Integer quantity;
    private Integer previousStock;
    private Integer newStock;
    private String referenceId; // e.g. orderId or adminId
    private LocalDateTime timestamp;
}
