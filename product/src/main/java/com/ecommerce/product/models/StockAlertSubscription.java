package com.ecommerce.product.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_alert_subscriptions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "productId"}),
        indexes = @Index(name = "idx_stock_alert_product", columnList = "productId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAlertSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Long productId;

    @CreationTimestamp
    private LocalDateTime subscribedAt;
}
