package com.possystem.backend.common.outbox.consumer;

import com.possystem.backend.common.outbox.entity.ProcessedEvent;
import com.possystem.backend.common.outbox.event.DomainEventEnvelope;
import com.possystem.backend.common.outbox.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * Guarantees At-Least-Once delivery converges to Effectively-Once processing per consumer
 * using database-backed (event_id, consumer_name) deduplication in isolated transactions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentConsumerExecutor {

    private final ProcessedEventRepository processedEventRepository;
    private final PlatformTransactionManager transactionManager;

    public boolean execute(PosEventConsumer consumer, DomainEventEnvelope envelope) {
        String eventId = envelope.getEventId();
        String consumerName = consumer.consumerName();

        if (processedEventRepository.existsByEventIdAndConsumerName(eventId, consumerName)) {
            log.debug("Idempotent skip: event {} already processed by consumer {}", eventId, consumerName);
            return false;
        }

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {
            Boolean executed = txTemplate.execute(status -> {
                if (processedEventRepository.existsByEventIdAndConsumerName(eventId, consumerName)) {
                    return Boolean.FALSE;
                }

                long startNanos = System.nanoTime();
                consumer.consume(envelope);
                long elapsedMs = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);

                ProcessedEvent record = ProcessedEvent.builder()
                        .eventId(eventId)
                        .consumerName(consumerName)
                        .eventType(envelope.getEventType())
                        .aggregateId(envelope.getAggregateId())
                        .status("PROCESSED")
                        .executionMs(elapsedMs)
                        .processedAt(LocalDateTime.now())
                        .build();
                processedEventRepository.save(record);
                return Boolean.TRUE;
            });
            return Boolean.TRUE.equals(executed);
        } catch (DataIntegrityViolationException ex) {
            if (processedEventRepository.existsByEventIdAndConsumerName(eventId, consumerName)) {
                log.info("Concurrent duplicate event {} safely ignored by consumer {}", eventId, consumerName);
                return false;
            }
            throw ex;
        }
    }
}
