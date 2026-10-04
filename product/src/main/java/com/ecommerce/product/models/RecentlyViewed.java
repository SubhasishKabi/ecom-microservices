package com.ecommerce.product.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * No unique constraint on purpose: a view is recorded as delete-existing-row-then-insert,
 * so viewedAt always reflects the most recent view.
 */
@Entity
@Table(name = "recently_viewed", indexes = @Index(name = "idx_recently_viewed_user", columnList = "userId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentlyViewed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private LocalDateTime viewedAt;
}
