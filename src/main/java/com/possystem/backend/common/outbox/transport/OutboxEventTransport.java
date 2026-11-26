package com.possystem.backend.common.outbox.transport;

import com.possystem.backend.common.outbox.event.DomainEventEnvelope;

/**
 * Abstraction for event delivery transport.
 * Default modular-monolith implementation dispatches directly to local idempotent consumers;
 * can be swapped with Kafka or an external message broker behind a feature flag.
 */
public interface OutboxEventTransport {
    String transportName();

    void publish(DomainEventEnvelope envelope) throws EventTransportException;
}
