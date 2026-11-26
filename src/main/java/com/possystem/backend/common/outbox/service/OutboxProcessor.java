package com.possystem.backend.common.outbox.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.possystem.backend.common.observability.PosBusinessMetrics;
import com.possystem.backend.common.observability.PosTracingHelper;
import com.possystem.backend.common.outbox.config.OutboxProperties;
import com.possystem.backend.common.outbox.entity.OutboxEvent;
import com.possystem.backend.common.outbox.event.DomainEventEnvelope;
import com.possystem.backend.common.outbox.kafka.KafkaPosEventConsumer;
import com.possystem.backend.common.outbox.repository.OutboxEventRepository;
import com.possystem.backend.common.outbox.transport.OutboxEventTransport;
import io.micrometer.tracing.Span;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Reliable Transactional Outbox worker featuring:
 * - Worker lease claiming + stale lock crash recovery
 * - Per-aggregate causal event ordering (via orderingKey)
 * - Exponential backoff retries
 * - Dead Letter Queue (DLQ) escalation & single/batch Event Replay
 * - Dynamic transport selection between Local Modular Monolith and Apache Kafka
 */
@Slf4j
@Component
public class OutboxProcessor {

    private static final List<OutboxEvent.OutboxStatus> ORDER_BLOCKING_STATUSES = List.of(
            OutboxEvent.OutboxStatus.PENDING,
            OutboxEvent.OutboxStatus.PROCESSING,
            OutboxEvent.OutboxStatus.FAILED,
            OutboxEvent.OutboxStatus.DEAD_LETTER
    );

    private final OutboxEventRepository outboxEventRepository;
    private final List<OutboxEventTransport> availableTransports;
    private final ObjectMapper objectMapper;
    private final OutboxProperties outboxProperties;
    private final PlatformTransactionManager transactionManager;
    private final ObjectProvider<KafkaPosEventConsumer> kafkaConsumerProvider;
    private final ObjectProvider<PosBusinessMetrics> businessMetricsProvider;
    private final ObjectProvider<PosTracingHelper> tracingHelperProvider;

    public OutboxProcessor(
            OutboxEventRepository outboxEventRepository,
            List<OutboxEventTransport> availableTransports,
            ObjectMapper objectMapper,
            OutboxProperties outboxProperties,
            PlatformTransactionManager transactionManager,
            ObjectProvider<KafkaPosEventConsumer> kafkaConsumerProvider) {
        this(outboxEventRepository, availableTransports, objectMapper, outboxProperties,
                transactionManager, kafkaConsumerProvider, null, null);
    }

    @Autowired
    public OutboxProcessor(
            OutboxEventRepository outboxEventRepository,
            List<OutboxEventTransport> availableTransports,
            ObjectMapper objectMapper,
            OutboxProperties outboxProperties,
            PlatformTransactionManager transactionManager,
            ObjectProvider<KafkaPosEventConsumer> kafkaConsumerProvider,
            ObjectProvider<PosBusinessMetrics> businessMetricsProvider,
            ObjectProvider<PosTracingHelper> tracingHelperProvider) {
        this.outboxEventRepository = outboxEventRepository;
        this.availableTransports = availableTransports != null ? availableTransports : List.of();
        this.objectMapper = objectMapper;
        this.outboxProperties = outboxProperties;
        this.transactionManager = transactionManager;
        this.kafkaConsumerProvider = kafkaConsumerProvider;
        this.businessMetricsProvider = businessMetricsProvider;
        this.tracingHelperProvider = tracingHelperProvider;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:2000}")
    public void scheduledPoll() {
        if (!outboxProperties.isEnabled() || !outboxProperties.isWorkerEnabled()) {
            return;
        }
        recoverStaleLocks();
        processOutboxEvents();
    }

    /**
     * Recovers events orphaned in PROCESSING status when a worker node crashes or times out.
     */
    @Transactional
    public int recoverStaleLocks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusSeconds(Math.max(1, outboxProperties.getLeaseTimeoutSeconds()));
        List<OutboxEvent> stale = outboxEventRepository.findStaleProcessingEvents(cutoff);
        if (stale.isEmpty()) {
            return 0;
        }

        int recovered = 0;
        for (OutboxEvent event : stale) {
            int retries = (event.getRetryCount() != null ? event.getRetryCount() : 0) + 1;
            int maxRetries = resolveMaxRetries(event);
            event.setRetryCount(retries);
            event.setLockedAt(null);
            event.setLockedBy(null);

            if (retries >= maxRetries) {
                event.setStatus(OutboxEvent.OutboxStatus.DEAD_LETTER);
                event.setDeadLetteredAt(now);
                event.setErrorMessage("Worker lease expired after max retries (worker crash recovery)");
                log.warn("Stale OutboxEvent {} moved to DEAD_LETTER after {} attempts", event.getId(), retries);
            } else {
                event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                event.setNextRetryAt(now);
                event.setErrorMessage("Worker lease expired (recovered from crashed worker)");
                log.warn("Recovered stale PROCESSING OutboxEvent {} -> FAILED (retry {}/{})",
                        event.getId(), retries, maxRetries);
            }
            outboxEventRepository.save(event);
            recovered++;
        }
        return recovered;
    }

    public int processOutboxEvents() {
        return processOutboxEvents(resolveActiveTransport());
    }

    public int processOutboxEvents(OutboxEventTransport transportOverride) {
        OutboxEventTransport activeTransport = transportOverride != null ? transportOverride : resolveActiveTransport();
        LocalDateTime now = LocalDateTime.now();
        int batchSize = Math.max(1, outboxProperties.getBatchSize());

        List<OutboxEvent> dueEvents = outboxEventRepository.findDueEvents(now, PageRequest.of(0, batchSize));
        if (dueEvents.isEmpty()) {
            return 0;
        }

        int processedCount = 0;
        for (OutboxEvent candidate : dueEvents) {
            if (isBlockedByEarlierEvent(candidate)) {
                log.debug("Delaying OutboxEvent {} ({}) because an earlier event with orderingKey={} is incomplete",
                        candidate.getId(), candidate.getEventType(), candidate.getOrderingKey());
                continue;
            }
            if (dispatchSingleEvent(candidate.getId(), activeTransport)) {
                processedCount++;
            }
        }
        return processedCount;
    }

    /**
     * Preserved single-event dispatch entrypoint for backward compatibility and tests.
     */
    public void dispatchSingleEvent(OutboxEvent event) {
        if (event == null || event.getId() == null) {
            return;
        }
        dispatchSingleEvent(event.getId(), resolveActiveTransport());
    }

    public boolean dispatchSingleEvent(String eventId, OutboxEventTransport transportOverride) {
        OutboxEventTransport activeTransport = transportOverride != null ? transportOverride : resolveActiveTransport();
        OutboxEvent claimed = claimEventForProcessing(eventId);
        if (claimed == null) {
            return false;
        }

        long startNanos = System.nanoTime();
        PosTracingHelper tracingHelper = tracingHelperProvider != null ? tracingHelperProvider.getIfAvailable() : null;
        PosBusinessMetrics businessMetrics = businessMetricsProvider != null ? businessMetricsProvider.getIfAvailable() : null;

        try {
            DomainEventEnvelope envelope = toEnvelope(claimed);
            if (tracingHelper != null) {
                tracingHelper.traceRunnable(
                        "pos.outbox.dispatch",
                        Span.Kind.CONSUMER,
                        claimed.getTraceParent(),
                        Map.of(
                                "pos.event.type", claimed.getEventType(),
                                "pos.aggregate.type", claimed.getAggregateType(),
                                "pos.transport", activeTransport.transportName()),
                        () -> activeTransport.publish(envelope));
            } else {
                activeTransport.publish(envelope);
            }
            markProcessed(eventId);
            if (businessMetrics != null) {
                businessMetrics.recordOutboxDispatch(
                        claimed.getEventType(), activeTransport.transportName(), "PROCESSED", System.nanoTime() - startNanos);
            }
            return true;
        } catch (Exception ex) {
            log.error("Lỗi khi điều phối OutboxEvent id={} type={}: {}",
                    eventId, claimed.getEventType(), ex.getMessage(), ex);
            markFailedWithBackoff(eventId, ex.getMessage());
            if (businessMetrics != null) {
                businessMetrics.recordOutboxDispatch(
                        claimed.getEventType(), activeTransport.transportName(), "FAILED", System.nanoTime() - startNanos);
            }
            return false;
        }
    }

    public OutboxEventTransport resolveActiveTransport() {
        String desired = outboxProperties.getTransport() != null ? outboxProperties.getTransport().trim() : "local";
        if ("kafka".equalsIgnoreCase(desired)) {
            for (OutboxEventTransport t : availableTransports) {
                if ("kafka".equalsIgnoreCase(t.transportName())) {
                    return t;
                }
            }
            log.warn("app.outbox.transport=kafka configured but Kafka transport bean is disabled; falling back to local transport");
        }
        for (OutboxEventTransport t : availableTransports) {
            if ("local-modular-monolith".equalsIgnoreCase(t.transportName())) {
                return t;
            }
        }
        return availableTransports.stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No OutboxEventTransport beans registered"));
    }

    private boolean isBlockedByEarlierEvent(OutboxEvent candidate) {
        if (candidate.getOrderingKey() == null || candidate.getOrderingKey().isBlank() || candidate.getCreatedAt() == null) {
            return false;
        }
        return outboxEventRepository.existsEarlierBlockingEvent(
                candidate.getOrderingKey(),
                candidate.getId(),
                candidate.getCreatedAt(),
                ORDER_BLOCKING_STATUSES);
    }

    private OutboxEvent claimEventForProcessing(String eventId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            return tx.execute(status -> {
                OutboxEvent event = outboxEventRepository.findById(eventId).orElse(null);
                if (event == null) {
                    return null;
                }
                if (event.getStatus() != OutboxEvent.OutboxStatus.PENDING
                        && event.getStatus() != OutboxEvent.OutboxStatus.FAILED) {
                    return null;
                }
                if (event.getStatus() == OutboxEvent.OutboxStatus.FAILED
                        && event.getNextRetryAt() != null
                        && event.getNextRetryAt().isAfter(LocalDateTime.now())) {
                    return null;
                }
                event.setStatus(OutboxEvent.OutboxStatus.PROCESSING);
                event.setLockedAt(LocalDateTime.now());
                event.setLockedBy(outboxProperties.getWorkerId());
                return outboxEventRepository.saveAndFlush(event);
            });
        } catch (ObjectOptimisticLockingFailureException ex) {
            log.debug("OutboxEvent {} already claimed by another worker", eventId);
            return null;
        }
    }

    private void markProcessed(String eventId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> outboxEventRepository.findById(eventId).ifPresent(event -> {
            event.setStatus(OutboxEvent.OutboxStatus.PROCESSED);
            event.setProcessedAt(LocalDateTime.now());
            event.setLockedAt(null);
            event.setLockedBy(null);
            event.setNextRetryAt(null);
            event.setErrorMessage(null);
            outboxEventRepository.save(event);
        }));
    }

    private void markFailedWithBackoff(String eventId, String rawError) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> outboxEventRepository.findById(eventId).ifPresent(event -> {
            LocalDateTime now = LocalDateTime.now();
            int retries = (event.getRetryCount() != null ? event.getRetryCount() : 0) + 1;
            int maxRetries = resolveMaxRetries(event);

            event.setRetryCount(retries);
            event.setLockedAt(null);
            event.setLockedBy(null);
            event.setErrorMessage(truncateError(rawError));

            if (retries >= maxRetries) {
                event.setStatus(OutboxEvent.OutboxStatus.DEAD_LETTER);
                event.setDeadLetteredAt(now);
                event.setNextRetryAt(null);
                log.warn("Sự kiện {} ({}) vượt quá {} lần thử lại -> Chuyển vào DEAD_LETTER.",
                        event.getId(), event.getEventType(), maxRetries);
            } else {
                long backoffMs = computeExponentialBackoffMs(retries);
                event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                event.setNextRetryAt(now.plusNanos(backoffMs * 1_000_000L));
                log.warn("Sự kiện {} ({}) thất bại lần {}/{} -> Lên lịch retry sau {} ms.",
                        event.getId(), event.getEventType(), retries, maxRetries, backoffMs);
            }
            outboxEventRepository.save(event);
        }));
    }

    public long computeExponentialBackoffMs(int retryAttempt) {
        long base = Math.max(1L, outboxProperties.getBaseBackoffMs());
        long max = Math.max(base, outboxProperties.getMaxBackoffMs());
        int exponent = Math.min(Math.max(retryAttempt - 1, 0), 20);
        long multiplier = 1L << exponent;
        if (base > max / multiplier) {
            return max;
        }
        return Math.min(base * multiplier, max);
    }

    private DomainEventEnvelope toEnvelope(OutboxEvent event) {
        Map<String, Object> payloadMap;
        try {
            payloadMap = objectMapper.readValue(event.getPayload(), new TypeReference<>() {});
        } catch (Exception ex) {
            payloadMap = Map.of("raw", event.getPayload());
        }

        return DomainEventEnvelope.builder()
                .eventId(event.getId())
                .eventType(event.getEventType())
                .eventVersion(event.getEventVersion() != null ? event.getEventVersion() : "1.0")
                .aggregateType(event.getAggregateType())
                .aggregateId(event.getAggregateId())
                .orderingKey(event.getOrderingKey())
                .correlationId(event.getCorrelationId())
                .traceParent(event.getTraceParent())
                .occurredAt(event.getCreatedAt())
                .payload(payloadMap)
                .build();
    }

    @Transactional
    public Map<String, Object> retryDeadLetters() {
        List<OutboxEvent> deadLetters = outboxEventRepository.findAllByStatusOrderByCreatedAtAsc(
                OutboxEvent.OutboxStatus.DEAD_LETTER, PageRequest.of(0, 100));

        LocalDateTime now = LocalDateTime.now();
        int retried = 0;
        for (OutboxEvent event : deadLetters) {
            prepareForReplay(event, now);
            retried++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("retriedCount", retried);
        result.put("timestamp", now);
        return result;
    }

    @Transactional
    public OutboxEvent replayEvent(String eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("OutboxEvent not found: " + eventId));
        prepareForReplay(event, LocalDateTime.now());
        return event;
    }

    private void prepareForReplay(OutboxEvent event, LocalDateTime now) {
        event.setStatus(OutboxEvent.OutboxStatus.PENDING);
        event.setRetryCount(0);
        event.setNextRetryAt(null);
        event.setLockedAt(null);
        event.setLockedBy(null);
        event.setErrorMessage(null);
        event.setDeadLetteredAt(null);
        event.setReplayedCount((event.getReplayedCount() != null ? event.getReplayedCount() : 0) + 1);
        event.setLastReplayedAt(now);
        outboxEventRepository.save(event);
    }

    public Page<OutboxEvent> listEvents(
            OutboxEvent.OutboxStatus status, String eventType, String aggregateId, Pageable pageable) {
        String normalizedType = (eventType != null && !eventType.isBlank()) ? eventType.trim() : null;
        String normalizedAggId = (aggregateId != null && !aggregateId.isBlank()) ? aggregateId.trim() : null;
        return outboxEventRepository.findFiltered(status, normalizedType, normalizedAggId, pageable);
    }

    public Map<String, Object> getOutboxStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("pending", outboxEventRepository.countByStatus(OutboxEvent.OutboxStatus.PENDING));
        stats.put("processing", outboxEventRepository.countByStatus(OutboxEvent.OutboxStatus.PROCESSING));
        stats.put("processed", outboxEventRepository.countByStatus(OutboxEvent.OutboxStatus.PROCESSED));
        stats.put("failed", outboxEventRepository.countByStatus(OutboxEvent.OutboxStatus.FAILED));
        stats.put("deadLetter", outboxEventRepository.countByStatus(OutboxEvent.OutboxStatus.DEAD_LETTER));
        stats.put("transport", resolveActiveTransport().transportName());

        KafkaPosEventConsumer kafkaConsumer = kafkaConsumerProvider != null ? kafkaConsumerProvider.getIfAvailable() : null;
        if (kafkaConsumer != null) {
            stats.put("kafkaEnabled", true);
            stats.put("kafkaConsumedCount", kafkaConsumer.getConsumedCount());
            stats.put("kafkaDltCount", kafkaConsumer.getDltCount());
        } else {
            stats.put("kafkaEnabled", false);
        }
        return stats;
    }

    private int resolveMaxRetries(OutboxEvent event) {
        if (event.getMaxRetries() != null && event.getMaxRetries() > 0) {
            return event.getMaxRetries();
        }
        return Math.max(1, outboxProperties.getMaxRetries());
    }

    private String truncateError(String rawError) {
        if (rawError == null) {
            return "Unknown dispatch failure";
        }
        return rawError.length() > 490 ? rawError.substring(0, 490) : rawError;
    }
}
