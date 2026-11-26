package com.possystem.backend.common.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.possystem.backend.common.observability.PosTracingHelper;
import com.possystem.backend.common.outbox.config.OutboxProperties;
import com.possystem.backend.common.outbox.entity.OutboxEvent;
import com.possystem.backend.common.outbox.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Publishes domain events into the outbox_events table atomically within the business transaction
 * (Transactional Outbox Pattern), supporting ordering keys, correlation IDs, and failure recording.
 */
@Slf4j
@Service
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final OutboxProperties outboxProperties;
    private final ObjectProvider<PosTracingHelper> tracingHelperProvider;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this(outboxEventRepository, objectMapper, new OutboxProperties(), null);
    }

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            OutboxProperties outboxProperties) {
        this(outboxEventRepository, objectMapper, outboxProperties, null);
    }

    @Autowired
    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            OutboxProperties outboxProperties,
            ObjectProvider<PosTracingHelper> tracingHelperProvider) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.outboxProperties = outboxProperties != null ? outboxProperties : new OutboxProperties();
        this.tracingHelperProvider = tracingHelperProvider;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent publishInCurrentTransaction(
            String aggregateType,
            String aggregateId,
            String eventType,
            String orderingKey,
            String correlationId,
            Object payload) {
        return publishInternal(aggregateType, aggregateId, eventType, orderingKey, correlationId, payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent publishInCurrentTransaction(
            String aggregateType, String aggregateId, String eventType, Object payload) {
        return publishInternal(aggregateType, aggregateId, eventType, defaultOrderingKey(aggregateType, aggregateId), aggregateId, payload);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public OutboxEvent publish(String aggregateType, String aggregateId, String eventType, Object payload) {
        return publishInternal(aggregateType, aggregateId, eventType, defaultOrderingKey(aggregateType, aggregateId), aggregateId, payload);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public OutboxEvent publish(
            String aggregateType,
            String aggregateId,
            String eventType,
            String orderingKey,
            String correlationId,
            Object payload) {
        return publishInternal(aggregateType, aggregateId, eventType, orderingKey, correlationId, payload);
    }

    /**
     * Records failure audit events (e.g. PaymentFailed) in an isolated transaction so the outer
     * business rollback does not erase visibility into failed payment attempts.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OutboxEvent publishFailureInNewTransaction(
            String aggregateType,
            String aggregateId,
            String eventType,
            String orderingKey,
            String correlationId,
            Object payload) {
        return publishInternal(aggregateType, aggregateId, eventType, orderingKey, correlationId, payload);
    }

    private OutboxEvent publishInternal(
            String aggregateType,
            String aggregateId,
            String eventType,
            String orderingKey,
            String correlationId,
            Object payload) {
        if (!outboxProperties.isEnabled()) {
            return null;
        }

        String payloadJson;
        try {
            payloadJson = (payload instanceof String s) ? s : objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Lỗi tuần tự hóa payload outbox cho sự kiện {}: {}", eventType, e.getMessage());
            payloadJson = "{}";
        }

        String effectiveAggregateId = aggregateId != null && !aggregateId.isBlank() ? aggregateId : "UNKNOWN";
        String effectiveOrderingKey = orderingKey != null && !orderingKey.isBlank()
                ? orderingKey
                : defaultOrderingKey(aggregateType, effectiveAggregateId);

        PosTracingHelper tracingHelper = tracingHelperProvider != null ? tracingHelperProvider.getIfAvailable() : null;
        String activeTraceParent = tracingHelper != null ? tracingHelper.currentTraceParent() : null;

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(effectiveAggregateId)
                .eventType(eventType)
                .eventVersion("1.0")
                .orderingKey(effectiveOrderingKey)
                .correlationId(correlationId != null ? correlationId : effectiveAggregateId)
                .traceParent(activeTraceParent)
                .payload(payloadJson)
                .status(OutboxEvent.OutboxStatus.PENDING)
                .retryCount(0)
                .maxRetries(Math.max(1, outboxProperties.getMaxRetries()))
                .replayedCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        OutboxEvent saved = outboxEventRepository.save(event);
        log.debug("Đã lưu OutboxEvent [{}] id={} orderingKey={}", eventType, saved.getId(), effectiveOrderingKey);
        return saved;
    }

    private String defaultOrderingKey(String aggregateType, String aggregateId) {
        String prefix = aggregateType != null ? aggregateType.toLowerCase() : "event";
        return prefix + ":" + (aggregateId != null ? aggregateId : "default");
    }
}
