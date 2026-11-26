package com.possystem.backend.common.outbox.transport;

import com.possystem.backend.common.outbox.consumer.IdempotentConsumerExecutor;
import com.possystem.backend.common.outbox.consumer.PosEventConsumer;
import com.possystem.backend.common.outbox.event.DomainEventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Modular-monolith event transport dispatching events directly to all matching
 * IdempotentConsumerExecutor-wrapped PosEventConsumers.
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class LocalOutboxEventTransport implements OutboxEventTransport {

    private final List<PosEventConsumer> consumers;
    private final IdempotentConsumerExecutor idempotentConsumerExecutor;

    @Override
    public String transportName() {
        return "local-modular-monolith";
    }

    @Override
    public void publish(DomainEventEnvelope envelope) throws EventTransportException {
        if (consumers == null || consumers.isEmpty()) {
            return;
        }

        for (PosEventConsumer consumer : consumers) {
            if (consumer.supports(envelope.getEventType())) {
                try {
                    idempotentConsumerExecutor.execute(consumer, envelope);
                } catch (Exception ex) {
                    throw new EventTransportException(
                            "Consumer [" + consumer.consumerName() + "] failed for event "
                                    + envelope.getEventType() + " (" + envelope.getEventId() + "): " + ex.getMessage(),
                            ex,
                            true);
                }
            }
        }
    }
}
