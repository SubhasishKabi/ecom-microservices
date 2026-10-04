package com.ecommerce.notification.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RefundCompletedEvent {
    private String eventId;
    private Long orderId;
    private String userId;
    private String refundId;
    private BigDecimal amount;
    private LocalDateTime timestamp;
}
