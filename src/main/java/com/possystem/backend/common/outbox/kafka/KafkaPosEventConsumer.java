package com.possystem.backend.common.outbox.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.possystem.backend.common.observability.PosBusinessMetrics;
import com.possystem.backend.common.observability.PosTracingHelper;
import com.possystem.backend.common.outbox.consumer.IdempotentConsumerExecutor;
import com.possystem.backend.common.outbox.consumer.PosEventConsumer;
import com.possystem.backend.common.outbox.entity.AuditEvent;
import com.possystem.backend.common.outbox.entity.OutboxEvent;
import com.possystem.backend.common.outbox.event.DomainEventEnvelope;
import com.possystem.backend.common.outbox.repository.AuditEventRepository;
import com.possystem.backend.common.outbox.repository.OutboxEventRepository;
import io.micrometer.tracing.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spring Kafka event consumer subscribing to POS domain topics with non-blocking
 * retry topics (.retry-0, .retry-1, ...) and Dead Letter Topic (.dlt) escalation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class KafkaPosEventConsumer {

    private final List<PosEventConsumer> consumers;
    private final IdempotentConsumerExecutor idempotentConsumerExecutor;
    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;
    private final AuditEventRepository auditEventRepository;
    private final ObjectProvider<PosTracingHelper> tracingHelperProvider;
    private final ObjectProvider<PosBusinessMetrics> businessMetricsProvider;

    private final AtomicLong consumedCount = new AtomicLong(0);
    private final AtomicLong dltCount = new AtomicLong(0);

    @RetryableTopic(
            attempts = "${app.kafka.retry-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${app.kafka.retry-initial-delay-ms:2000}",
                    multiplierExpression = "${app.kafka.retry-multiplier:2.0}",
                    maxDelayExpression = "${app.kafka.retry-max-delay-ms:30000}"
            ),
            retryTopicSuffix = ".retry",
            dltTopicSuffix = ".dlt",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            kafkaTemplate = "stringKafkaTemplate",
            autoCreateTopics = "true"
    )
    @KafkaListener(
            topics = {
                    "${app.kafka.topics.orders:pos.orders.events}",
                    "${app.kafka.topics.payments:pos.payments.events}",
                    "${app.kafka.topics.inventory:pos.inventory.events}",
                    "${app.kafka.topics.loyalty:pos.loyalty.events}",
                    "${app.kafka.topics.vouchers:pos.vouchers.events}",
                    "${app.kafka.topics.shifts:pos.shifts.events}"
            },
            groupId = "${app.kafka.consumer-group:pos-monolith-workers}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        DomainEventEnvelope envelope;
        try {
            envelope = objectMapper.readValue(record.value(), DomainEventEnvelope.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Malformed Kafka event payload on topic " + record.topic(), ex);
        }

        String parentTraceParent = extractTraceParent(record, envelope);
        PosTracingHelper tracingHelper = tracingHelperProvider != null ? tracingHelperProvider.getIfAvailable() : null;
        PosBusinessMetrics metrics = businessMetricsProvider != null ? businessMetricsProvider.getIfAvailable() : null;

        Runnable dispatchTask = () -> {
            for (PosEventConsumer consumer : consumers) {
                if (consumer.supports(envelope.getEventType())) {
                    idempotentConsumerExecutor.execute(consumer, envelope);
                }
            }
            consumedCount.incrementAndGet();
            if (metrics != null) {
                metrics.recordKafkaConsume(record.topic(), "SUCCESS");
            }
        };

        if (tracingHelper != null) {
            tracingHelper.traceRunnable(
                    "pos.kafka.consume",
                    Span.Kind.CONSUMER,
                    parentTraceParent,
                    Map.of(
                            "messaging.system", "kafka",
                            "messaging.destination.name", record.topic(),
                            "pos.event.type", envelope.getEventType()),
                    dispatchTask);
        } else {
            dispatchTask.run();
        }
    }

    private String extractTraceParent(ConsumerRecord<String, String> record, DomainEventEnvelope envelope) {
        if (record != null && record.headers() != null) {
            Header h = record.headers().lastHeader("traceparent");
            if (h != null && h.value() != null) {
                return new String(h.value(), StandardCharsets.UTF_8);
            }
        }
        return envelope != null ? envelope.getTraceParent() : null;
    }

    @DltHandler
    @Transactional
    public void onDltMessage(
            ConsumerRecord<String, String> record,
            @org.springframework.messaging.handler.annotation.Header(value = KafkaHeaders.ORIGINAL_TOPIC, required = false) String originalTopic,
            @org.springframework.messaging.handler.annotation.Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage) {
        long totalDlt = dltCount.incrementAndGet();
        PosBusinessMetrics metrics = businessMetricsProvider != null ? businessMetricsProvider.getIfAvailable() : null;
        if (metrics != null) {
            metrics.recordKafkaConsume(originalTopic != null ? originalTopic : record.topic(), "DLT");
        }
        log.error("Kafka DLT message received [count={}]: topic={} key={} originalTopic={} error={}",
                totalDlt, record.topic(), record.key(), originalTopic, exceptionMessage);

        try {
            DomainEventEnvelope envelope = objectMapper.readValue(record.value(), DomainEventEnvelope.class);
            if (envelope.getEventId() != null) {
                outboxEventRepository.findById(envelope.getEventId()).ifPresent(event -> {
                    event.setStatus(OutboxEvent.OutboxStatus.DEAD_LETTER);
                    event.setDeadLetteredAt(LocalDateTime.now());
                    String err = "Kafka DLT (" + record.topic() + "): " + (exceptionMessage != null ? exceptionMessage : "Consumer failed");
                    event.setErrorMessage(err.length() > 490 ? err.substring(0, 490) : err);
                    outboxEventRepository.save(event);
                });

                String dltAuditId = "dlt:" + envelope.getEventId();
                if (!auditEventRepository.existsByEventId(dltAuditId)) {
                    auditEventRepository.save(AuditEvent.builder()
                            .eventId(dltAuditId)
                            .eventType("KAFKA_DLT_RECEIVED")
                            .aggregateType(envelope.getAggregateType() != null ? envelope.getAggregateType() : "KAFKA")
                            .aggregateId(envelope.getAggregateId() != null ? envelope.getAggregateId() : record.topic())
                            .correlationId(envelope.getCorrelationId())
                            .actor("KAFKA_DLT_HANDLER")
                            .summary("Kafka DLT từ topic " + (originalTopic != null ? originalTopic : record.topic())
                                    + ": " + (exceptionMessage != null ? exceptionMessage : "Unknown failure"))
                            .payloadJson(record.value())
                            .occurredAt(envelope.getOccurredAt() != null ? envelope.getOccurredAt() : LocalDateTime.now())
                            .recordedAt(LocalDateTime.now())
                            .build());
                }
            }
        } catch (Exception parseEx) {
            log.warn("Could not parse Kafka DLT message payload: {}", parseEx.getMessage());
        }
    }

    public long getConsumedCount() {
        return consumedCount.get();
    }

    public long getDltCount() {
        return dltCount.get();
    }
}
