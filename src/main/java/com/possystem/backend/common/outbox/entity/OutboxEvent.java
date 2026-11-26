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
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
        @Index(name = "idx_outbox_aggregate", columnList = "aggregate_type, aggregate_id"),
        @Index(name = "idx_outbox_poll", columnList = "status, next_retry_at, created_at"),
        @Index(name = "idx_outbox_lock", columnList = "status, locked_at"),
        @Index(name = "idx_outbox_ordering", columnList = "ordering_key, created_at")
})
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    String aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    String eventType;

    @Column(name = "event_version", nullable = false, length = 20)
    @Builder.Default
    String eventVersion = "1.0";

    @Column(name = "correlation_id", length = 100)
    String correlationId;

    @Column(name = "ordering_key", length = 120)
    String orderingKey;

    @Column(name = "trace_parent", length = 128)
    String traceParent;

    @Column(columnDefinition = "TEXT", nullable = false)
    String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    OutboxStatus status;

    @Column(name = "retry_count")
    @Builder.Default
    Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    Integer maxRetries = 5;

    @Column(name = "next_retry_at")
    LocalDateTime nextRetryAt;

    @Column(name = "locked_at")
    LocalDateTime lockedAt;

    @Column(name = "locked_by", length = 100)
    String lockedBy;

    @Column(name = "error_message", length = 500)
    String errorMessage;

    @Column(name = "processed_at")
    LocalDateTime processedAt;

    @Column(name = "dead_lettered_at")
    LocalDateTime deadLetteredAt;

    @Column(name = "replayed_count", nullable = false)
    @Builder.Default
    Integer replayedCount = 0;

    @Column(name = "last_replayed_at")
    LocalDateTime lastReplayedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @Version
    @Builder.Default
    Long version = 0L;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = OutboxStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (maxRetries == null) {
            maxRetries = 5;
        }
        if (replayedCount == null) {
            replayedCount = 0;
        }
        if (eventVersion == null || eventVersion.isBlank()) {
            eventVersion = "1.0";
        }
    }

    public enum OutboxStatus {
        PENDING,
        PROCESSING,
        PROCESSED,
        FAILED,
        DEAD_LETTER
    }
}
