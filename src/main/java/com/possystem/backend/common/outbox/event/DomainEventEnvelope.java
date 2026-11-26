package com.possystem.backend.common.outbox.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standardized schema envelope passed through Transactional Outbox and consumed asynchronously.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DomainEventEnvelope {
    String eventId;
    String eventType;
    @Builder.Default
    String eventVersion = "1.0";
    String aggregateType;
    String aggregateId;
    String orderingKey;
    String correlationId;
    String traceParent;
    LocalDateTime occurredAt;
    Map<String, Object> payload;
}
