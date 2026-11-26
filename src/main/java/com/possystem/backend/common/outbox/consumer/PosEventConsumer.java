package com.possystem.backend.common.outbox.consumer;

import com.possystem.backend.common.outbox.event.DomainEventEnvelope;

/**
 * Contract for asynchronous domain event consumers running inside Modular Monolith workers.
 */
public interface PosEventConsumer {

    /**
     * Unique stable identifier used in processed_events for per-consumer idempotency.
     */
    String consumerName();

    boolean supports(String eventType);

    void consume(DomainEventEnvelope envelope);
}
