package com.possystem.backend.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.possystem.backend.common.outbox.consumer.PosEventConsumer;
import com.possystem.backend.common.outbox.entity.*;
import com.possystem.backend.common.outbox.event.DomainEventEnvelope;
import com.possystem.backend.common.outbox.event.PosEventType;
import com.possystem.backend.common.outbox.kafka.KafkaOutboxEventTransport;
import com.possystem.backend.common.outbox.kafka.KafkaPosEventConsumer;
import com.possystem.backend.common.outbox.kafka.KafkaProperties;
import com.possystem.backend.common.outbox.kafka.KafkaTopicResolver;
import com.possystem.backend.common.outbox.repository.*;
import com.possystem.backend.common.outbox.service.OutboxProcessor;
import com.possystem.backend.common.outbox.service.OutboxPublisher;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pos_kafka_test_db;MODE=MySQL;NON_KEYWORDS=USER,VALUE;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "app.outbox.enabled=true",
        "app.outbox.worker-enabled=false",
        "app.outbox.transport=kafka",
        "app.outbox.base-backoff-ms=50",
        "app.outbox.max-backoff-ms=300",
        "app.kafka.enabled=true",
        "app.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=true",
        "app.kafka.consumer-group=pos-kafka-integration-group",
        "app.kafka.send-timeout-ms=5000",
        "app.kafka.retry-attempts=3",
        "app.kafka.retry-initial-delay-ms=100",
        "app.kafka.retry-multiplier=1.5",
        "app.kafka.retry-max-delay-ms=400",
        "app.kafka.topics.partitions=2",
        "app.kafka.topics.replicas=1"
})
@EmbeddedKafka(
        partitions = 2,
        topics = {
                "pos.orders.events",
                "pos.payments.events",
                "pos.inventory.events",
                "pos.loyalty.events",
                "pos.vouchers.events",
                "pos.shifts.events"
        }
)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KafkaEventDrivenIntegrationTest {

    @TestConfiguration
    static class KafkaFailureTestConsumerConfig {
        @Bean
        @Order(100)
        PosEventConsumer dltSimulationTestConsumer() {
            return new PosEventConsumer() {
                @Override
                public String consumerName() {
                    return "KafkaDltTestProbeConsumer";
                }

                @Override
                public boolean supports(String eventType) {
                    return true;
                }

                @Override
                public void consume(DomainEventEnvelope envelope) {
                    Map<String, Object> payload = envelope.getPayload();
                    if (payload != null && Boolean.TRUE.equals(payload.get("simulateDltFailure"))) {
                        throw new IllegalStateException("Simulated downstream crash for Kafka Retry/DLT test");
                    }
                }
            };
        }
    }

    @Autowired private OutboxPublisher outboxPublisher;
    @Autowired private OutboxProcessor outboxProcessor;
    @Autowired private KafkaPosEventConsumer kafkaPosEventConsumer;
    @Autowired private KafkaTopicResolver topicResolver;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private KafkaTemplate<String, String> stringKafkaTemplate;

    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private ProcessedEventRepository processedEventRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private InvoiceRecordRepository invoiceRecordRepository;
    @Autowired private DailySalesProjectionRepository projectionRepository;

    @BeforeEach
    void resetState() {
        outboxEventRepository.deleteAll();
        processedEventRepository.deleteAll();
        auditEventRepository.deleteAll();
        invoiceRecordRepository.deleteAll();
        projectionRepository.deleteAll();
    }

    @Test
    @DisplayName("1. Kafka Producer -> Topic Routing -> Consumer Group -> Async Invoice, Audit & Projection")
    void kafkaEndToEnd_ProducerToConsumerGroup_ProcessesAllEvents() throws Exception {
        assertEquals("kafka", outboxProcessor.resolveActiveTransport().transportName());

        OutboxEvent orderEvt = outboxPublisher.publish(
                "ORDER",
                "ord-kaf-1",
                PosEventType.ORDER_CREATED,
                "order:ord-kaf-1",
                "ORD-KAF-1",
                Map.of("orderId", "ord-kaf-1", "orderCode", "ORD-KAF-1"));

        String payId = "pay-kaf-" + UUID.randomUUID();
        OutboxEvent payEvt = outboxPublisher.publish(
                "PAYMENT",
                payId,
                PosEventType.PAYMENT_COMPLETED,
                "order:ord-kaf-1",
                "ORD-KAF-1",
                Map.of(
                        "paymentId", payId,
                        "orderId", "ord-kaf-1",
                        "orderCode", "ORD-KAF-1",
                        "amount", 95000,
                        "method", "BANK_TRANSFER"
                ));

        OutboxEvent shiftEvt = outboxPublisher.publish(
                "SHIFT",
                "shift-kaf-1",
                PosEventType.SHIFT_CLOSED,
                "shift:shift-kaf-1",
                "SHIFT-KAF-1",
                Map.of("shiftId", "shift-kaf-1", "shiftCode", "SHIFT-KAF-1"));

        int dispatched = outboxProcessor.processOutboxEvents();
        assertEquals(3, dispatched);

        assertEquals(OutboxEvent.OutboxStatus.PROCESSED, outboxEventRepository.findById(orderEvt.getId()).orElseThrow().getStatus());
        assertEquals(OutboxEvent.OutboxStatus.PROCESSED, outboxEventRepository.findById(payEvt.getId()).orElseThrow().getStatus());
        assertEquals(OutboxEvent.OutboxStatus.PROCESSED, outboxEventRepository.findById(shiftEvt.getId()).orElseThrow().getStatus());

        awaitCondition(10000, () ->
                invoiceRecordRepository.existsByPaymentId(payId)
                        && auditEventRepository.existsByEventId(orderEvt.getId())
                        && auditEventRepository.existsByEventId(payEvt.getId())
                        && auditEventRepository.existsByEventId(shiftEvt.getId()));

        DailySalesProjection proj = projectionRepository.findByMetricDate(LocalDate.now()).orElse(null);
        assertNotNull(proj);
        assertEquals(1L, proj.getOrdersCreated());
        assertEquals(1L, proj.getPaymentsCompleted());
        assertEquals(1L, proj.getShiftsClosed());
        assertEquals(0, BigDecimal.valueOf(95000).compareTo(proj.getTotalRevenue()));
    }

    @Test
    @DisplayName("2. Kafka @RetryableTopic (.retry) -> @DltHandler (.dlt) Escalation & Audit Trail")
    void kafkaRetryAndDlt_ConsumerFailureEscalatesToDeadLetterTopic() throws Exception {
        long initialDltCount = kafkaPosEventConsumer.getDltCount();

        OutboxEvent failingEvt = outboxPublisher.publish(
                "ORDER",
                "ord-dlt-1",
                PosEventType.ORDER_CREATED,
                "order:ord-dlt-1",
                "ORD-DLT-1",
                Map.of(
                        "orderId", "ord-dlt-1",
                        "orderCode", "ORD-DLT-1",
                        "simulateDltFailure", true
                ));

        assertEquals(1, outboxProcessor.processOutboxEvents());

        String expectedDltAuditId = "dlt:" + failingEvt.getId();
        awaitCondition(15000, () ->
                kafkaPosEventConsumer.getDltCount() > initialDltCount
                        && auditEventRepository.existsByEventId(expectedDltAuditId));

        OutboxEvent updatedOutbox = outboxEventRepository.findById(failingEvt.getId()).orElseThrow();
        assertEquals(OutboxEvent.OutboxStatus.DEAD_LETTER, updatedOutbox.getStatus());
        assertNotNull(updatedOutbox.getDeadLetteredAt());
        assertTrue(updatedOutbox.getErrorMessage().contains("Kafka DLT"));
    }

    @Test
    @DisplayName("3. Kafka Duplicate Delivery on Topic Protected by IdempotentConsumerExecutor")
    void kafkaDuplicateMessage_ProcessedStrictlyOnce() throws Exception {
        String payId = "pay-kaf-dup-" + UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        DomainEventEnvelope envelope = DomainEventEnvelope.builder()
                .eventId(eventId)
                .eventType(PosEventType.PAYMENT_COMPLETED)
                .eventVersion("1.0")
                .aggregateType("PAYMENT")
                .aggregateId(payId)
                .orderingKey("order:ord-dup-1")
                .correlationId("ORD-DUP-1")
                .occurredAt(LocalDateTime.now())
                .payload(Map.of(
                        "paymentId", payId,
                        "orderId", "ord-dup-1",
                        "orderCode", "ORD-DUP-1",
                        "amount", 150000,
                        "method", "CASH"
                ))
                .build();

        String topic = topicResolver.resolveTopic(envelope);
        String json = objectMapper.writeValueAsString(envelope);

        // Send the exact same Kafka record twice
        stringKafkaTemplate.send(topic, envelope.getOrderingKey(), json).get();
        stringKafkaTemplate.send(topic, envelope.getOrderingKey(), json).get();

        awaitCondition(10000, () -> invoiceRecordRepository.existsByPaymentId(payId));
        Thread.sleep(300); // allow duplicate record delivery to finish safely

        assertEquals(1L, invoiceRecordRepository.count());
        assertEquals(1L, auditEventRepository.count());
        DailySalesProjection proj = projectionRepository.findByMetricDate(LocalDate.now()).orElseThrow();
        assertEquals(1L, proj.getPaymentsCompleted());
        assertEquals(0, BigDecimal.valueOf(150000).compareTo(proj.getTotalRevenue()));
    }

    @Test
    @DisplayName("4. Real Kafka Unreachable Disconnect -> Outbox Exponential Backoff -> DLQ -> Broker Recovery & Replay")
    void kafkaBrokerDisconnect_TriggersOutboxDlq_AndRecoversOnReplay() throws Exception {
        OutboxEvent event = outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId("ord-kaf-down")
                .eventType(PosEventType.ORDER_CREATED)
                .orderingKey("order:ord-kaf-down")
                .correlationId("ORD-KAF-DOWN")
                .payload("{\"orderId\":\"ord-kaf-down\",\"orderCode\":\"ORD-KAF-DOWN\"}")
                .status(OutboxEvent.OutboxStatus.PENDING)
                .retryCount(0)
                .maxRetries(2)
                .createdAt(LocalDateTime.now())
                .build());

        // Real KafkaProducer pointing to dead port with fast 800ms timeout
        Map<String, Object> deadProps = new HashMap<>();
        deadProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:19999");
        deadProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        deadProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        deadProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 800);
        deadProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 800);
        DefaultKafkaProducerFactory<String, String> deadPf = new DefaultKafkaProducerFactory<>(deadProps);
        KafkaTemplate<String, String> deadTemplate = new KafkaTemplate<>(deadPf);
        KafkaProperties shortTimeoutProps = new KafkaProperties();
        shortTimeoutProps.setSendTimeoutMs(800);
        KafkaOutboxEventTransport deadKafkaTransport = new KafkaOutboxEventTransport(
                deadTemplate, topicResolver, shortTimeoutProps, objectMapper);

        try {
            // Attempt 1 -> FAILED with backoff
            assertEquals(0, outboxProcessor.processOutboxEvents(deadKafkaTransport));
            OutboxEvent fail1 = outboxEventRepository.findById(event.getId()).orElseThrow();
            assertEquals(OutboxEvent.OutboxStatus.FAILED, fail1.getStatus());
            assertEquals(1, fail1.getRetryCount());
            assertTrue(fail1.getErrorMessage().contains("Kafka publish failed"));

            // Attempt 2 -> DEAD_LETTER
            fail1.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
            outboxEventRepository.save(fail1);
            assertEquals(0, outboxProcessor.processOutboxEvents(deadKafkaTransport));

            OutboxEvent dead = outboxEventRepository.findById(event.getId()).orElseThrow();
            assertEquals(OutboxEvent.OutboxStatus.DEAD_LETTER, dead.getStatus());
            assertNotNull(dead.getDeadLetteredAt());
        } finally {
            deadPf.destroy();
        }

        // Broker comes back online -> Replay DLQ using active EmbeddedKafka transport
        outboxProcessor.retryDeadLetters();
        assertEquals(1, outboxProcessor.processOutboxEvents());

        assertEquals(OutboxEvent.OutboxStatus.PROCESSED, outboxEventRepository.findById(event.getId()).orElseThrow().getStatus());
        awaitCondition(10000, () -> auditEventRepository.existsByEventId(event.getId()));
    }

    private void awaitCondition(long timeoutMs, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        assertTrue(condition.getAsBoolean(), "Condition did not become true within " + timeoutMs + " ms");
    }
}
