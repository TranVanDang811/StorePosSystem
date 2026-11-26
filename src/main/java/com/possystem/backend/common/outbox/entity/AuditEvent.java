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
@Table(name = "audit_events", uniqueConstraints = {
        @UniqueConstraint(name = "uq_audit_event_id", columnNames = {"event_id"})
}, indexes = {
        @Index(name = "idx_audit_aggregate", columnList = "aggregate_type, aggregate_id, occurred_at"),
        @Index(name = "idx_audit_type_time", columnList = "event_type, occurred_at")
})
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(name = "event_id", nullable = false, length = 64)
    String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    String aggregateId;

    @Column(name = "correlation_id", length = 100)
    String correlationId;

    @Column(length = 100)
    String actor;

    @Column(nullable = false, length = 500)
    String summary;

    @Column(name = "payload_json", columnDefinition = "LONGTEXT", nullable = false)
    String payloadJson;

    @Column(name = "occurred_at", nullable = false)
    LocalDateTime occurredAt;

    @Column(name = "recorded_at", nullable = false)
    LocalDateTime recordedAt;
}
