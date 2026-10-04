package com.ecommerce.order.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_events")
@Data
@NoArgsConstructor
public class ProcessedEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String eventId;

    private String eventType;
    private String status;
    private LocalDateTime timestamp;

    public ProcessedEvent(String eventId, String eventType, String status, LocalDateTime timestamp) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.status = status;
        this.timestamp = timestamp;
    }
}
