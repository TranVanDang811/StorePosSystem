package com.possystem.backend.common.outbox.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "processed_events", uniqueConstraints = {
        @UniqueConstraint(name = "uq_processed_event_consumer", columnNames = {"event_id", "consumer_name"})
}, indexes = {
        @Index(name = "idx_processed_event_type", columnList = "event_type, processed_at"),
        @Index(name = "idx_processed_aggregate", columnList = "aggregate_id")
})
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(name = "event_id", nullable = false, length = 64)
    String eventId;

    @Column(name = "consumer_name", nullable = false, length = 100)
    String consumerName;

    @Column(name = "event_type", nullable = false, length = 64)
    String eventType;

    @Column(name = "aggregate_id", length = 64)
    String aggregateId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    String status = "PROCESSED";

    @Column(name = "execution_ms")
    Long executionMs;

    @Column(name = "processed_at", nullable = false)
    LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        if (processedAt == null) {
            processedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "PROCESSED";
        }
    }
}
