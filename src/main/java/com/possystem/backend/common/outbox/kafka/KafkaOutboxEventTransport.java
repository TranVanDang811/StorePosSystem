package com.possystem.backend.common.outbox.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.possystem.backend.common.observability.PosTracingHelper;
import com.possystem.backend.common.outbox.event.DomainEventEnvelope;
import com.possystem.backend.common.outbox.transport.EventTransportException;
import com.possystem.backend.common.outbox.transport.OutboxEventTransport;
import io.micrometer.tracing.Span;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Publishes Outbox DomainEventEnvelopes into partitioned Apache Kafka topics
 * keyed by orderingKey so per-aggregate causal ordering is preserved at the broker level.
 */
@Slf4j
@Component("kafkaOutboxEventTransport")
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class KafkaOutboxEventTransport implements OutboxEventTransport {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTopicResolver topicResolver;
    private final KafkaProperties kafkaProperties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<PosTracingHelper> tracingHelperProvider;

    public KafkaOutboxEventTransport(
            @Qualifier("stringKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            KafkaTopicResolver topicResolver,
            KafkaProperties kafkaProperties,
            ObjectMapper objectMapper) {
        this(kafkaTemplate, topicResolver, kafkaProperties, objectMapper, null);
    }

    @Autowired
    public KafkaOutboxEventTransport(
            @Qualifier("stringKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            KafkaTopicResolver topicResolver,
            KafkaProperties kafkaProperties,
            ObjectMapper objectMapper,
            ObjectProvider<PosTracingHelper> tracingHelperProvider) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicResolver = topicResolver;
        this.kafkaProperties = kafkaProperties;
        this.objectMapper = objectMapper;
        this.tracingHelperProvider = tracingHelperProvider;
    }

    @Override
    public String transportName() {
        return "kafka";
    }

    @Override
    public void publish(DomainEventEnvelope envelope) throws EventTransportException {
        String topic = topicResolver.resolveTopic(envelope);
        PosTracingHelper tracingHelper = tracingHelperProvider != null ? tracingHelperProvider.getIfAvailable() : null;
        if (tracingHelper != null) {
            tracingHelper.traceChecked(
                    "pos.kafka.publish",
                    Span.Kind.PRODUCER,
                    envelope.getTraceParent(),
                    Map.of(
                            "messaging.system", "kafka",
                            "messaging.destination.name", topic,
                            "pos.event.type", envelope.getEventType()),
                    () -> {
                        sendInternal(envelope, topic, tracingHelper.currentTraceParent());
                        return null;
                    });
        } else {
            sendInternal(envelope, topic, envelope.getTraceParent());
        }
    }

    private void sendInternal(DomainEventEnvelope envelope, String topic, String activeTraceParent)
            throws EventTransportException {
        String key = envelope.getOrderingKey() != null && !envelope.getOrderingKey().isBlank()
                ? envelope.getOrderingKey()
                : envelope.getAggregateId();

        try {
            String json = objectMapper.writeValueAsString(envelope);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, json);
            addHeader(record, "X-Event-Id", envelope.getEventId());
            addHeader(record, "X-Event-Type", envelope.getEventType());
            addHeader(record, "X-Event-Version", envelope.getEventVersion());
            addHeader(record, "X-Correlation-Id", envelope.getCorrelationId());
            addHeader(record, "traceparent", activeTraceParent != null ? activeTraceParent : envelope.getTraceParent());

            long timeoutMs = Math.max(500L, kafkaProperties.getSendTimeoutMs());
            var result = kafkaTemplate.send(record).get(timeoutMs, TimeUnit.MILLISECONDS);
            log.debug("Kafka published event {} ({}) -> topic={} partition={} offset={}",
                    envelope.getEventType(),
                    envelope.getEventId(),
                    topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new EventTransportException("Interrupted sending event to Kafka topic " + topic, ie, true);
        } catch (Exception ex) {
            throw new EventTransportException(
                    "Kafka publish failed for event " + envelope.getEventId() + " on topic " + topic + ": " + ex.getMessage(),
                    ex,
                    true);
        }
    }

    private void addHeader(ProducerRecord<String, String> record, String header, String value) {
        if (value != null && !value.isBlank()) {
            record.headers().add(new RecordHeader(header, value.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
