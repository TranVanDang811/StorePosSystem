package com.possystem.backend.observability;

import com.possystem.backend.common.cache.CacheNames;
import com.possystem.backend.common.cache.PosCacheService;
import com.possystem.backend.common.enums.PaymentMethod;
import com.possystem.backend.common.observability.PosBusinessMetrics;
import com.possystem.backend.common.observability.PosTracingHelper;
import com.possystem.backend.common.observability.TraceSanitizer;
import com.possystem.backend.common.outbox.entity.OutboxEvent;
import com.possystem.backend.common.outbox.event.PosEventType;
import com.possystem.backend.common.outbox.repository.OutboxEventRepository;
import com.possystem.backend.common.outbox.service.OutboxProcessor;
import com.possystem.backend.common.outbox.service.OutboxPublisher;
import io.micrometer.tracing.Span;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "management.tracing.sampling.probability=1.0",
        "app.outbox.worker-enabled=false",
        "app.outbox.transport=local"
})
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("test")
@DirtiesContext
class ObservabilityAndTracingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PosBusinessMetrics businessMetrics;

    @Autowired
    private PosTracingHelper posTracingHelper;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxProcessor outboxProcessor;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Prometheus /actuator/prometheus exports HTTP, JVM, Hikari, Redis cache, Outbox & Business metrics with zero PII")
    void prometheusEndpoint_ExposesFullObservabilityMetrics_WithoutPii() throws Exception {
        // Trigger HTTP requests (twice so Redis cache records both miss/put and hit) + business metrics + outbox record
        mockMvc.perform(get("/products?page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"));

        mockMvc.perform(get("/products?page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"));

        businessMetrics.recordOrderCreated(BigDecimal.valueOf(55000));
        businessMetrics.recordOrderCancelled("CASHIER_CANCELLED");
        businessMetrics.recordPaymentCompleted(PaymentMethod.CASH, BigDecimal.valueOf(55000), "POS");
        businessMetrics.recordPaymentFailed(PaymentMethod.VNPAY, "Khách đưa không đủ tiền 0901234567", "POS");
        businessMetrics.recordInventoryAdjustment("SALE", -2);
        businessMetrics.recordVoucherRedemption("SUCCESS", BigDecimal.valueOf(5000));
        businessMetrics.recordShiftOpened("MAIN_COUNTER");
        businessMetrics.recordShiftClosed("MAIN_COUNTER", BigDecimal.valueOf(1000));
        businessMetrics.recordExternalCall("cloudinary", "upload", "SUCCESS", 12_000_000L);

        outboxPublisher.publish("ORDER", "obs-order-1", PosEventType.ORDER_CREATED,
                Map.of("orderId", "obs-order-1", "finalAmount", 55000));

        MvcResult result = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertAll(
                () -> assertTrue(body.contains("http_server_requests_seconds_count"), "Missing HTTP count metric"),
                () -> assertTrue(body.contains("jvm_memory_used_bytes"), "Missing JVM heap metric"),
                () -> assertTrue(body.contains("process_cpu_usage"), "Missing CPU usage metric"),
                () -> assertTrue(body.contains("hikaricp_connections_active"), "Missing HikariCP metric"),
                () -> assertTrue(body.contains("pos_cache_hits_total"), "Missing cache hits metric"),
                () -> assertTrue(body.contains("pos_cache_hit_rate_percent"), "Missing cache hit rate metric"),
                () -> assertTrue(body.contains("pos_orders_placed_total"), "Missing business order placed metric"),
                () -> assertTrue(body.contains("pos_orders_cancelled_total"), "Missing business order cancelled metric"),
                () -> assertTrue(body.contains("pos_payments_completed_total"), "Missing business payment completed metric"),
                () -> assertTrue(body.contains("pos_payments_failed_total"), "Missing business payment failed metric"),
                () -> assertTrue(body.contains("pos_inventory_adjustments_total"), "Missing inventory adjustment metric"),
                () -> assertTrue(body.contains("pos_vouchers_redemptions_total"), "Missing voucher redemption metric"),
                () -> assertTrue(body.contains("pos_shifts_opened_total"), "Missing shift opened metric"),
                () -> assertTrue(body.contains("pos_shifts_closed_total"), "Missing shift closed metric"),
                () -> assertTrue(body.contains("pos_external_requests_seconds_count"), "Missing external latency metric"),
                () -> assertTrue(body.contains("pos_outbox_events_count"), "Missing outbox backlog gauge")
        );

        // Strict PII / Secret isolation checks on exported Prometheus scrape output
        assertFalse(body.contains("0901234567"), "PII phone number must NEVER appear in Prometheus labels");
        assertFalse(body.contains("Bearer"), "JWT must NEVER appear in Prometheus labels");
        assertFalse(body.contains("Khách đưa"), "Raw user message must NEVER appear in Prometheus labels");
    }

    @Test
    @DisplayName("W3C traceparent propagates from HTTP client header through OutboxEvent to background worker")
    void w3cTraceParent_PropagatesAcrossHttpAndOutboxWorker() throws Exception {
        String incomingTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String incomingTraceParent = "00-" + incomingTraceId + "-00f067aa0ba902b7-01";

        mockMvc.perform(get("/actuator/health").header("traceparent", incomingTraceParent))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", incomingTraceId));

        OutboxEvent created = posTracingHelper.traceSupplier(
                "pos.http.checkout",
                Span.Kind.SERVER,
                incomingTraceParent,
                Map.of("pos.flow", "checkout", "authorization", "Bearer secret-jwt-value"),
                () -> outboxPublisher.publish(
                        "ORDER",
                        "ord-trace-e2e-1",
                        PosEventType.ORDER_CREATED,
                        Map.of("orderId", "ord-trace-e2e-1", "finalAmount", 79000)));

        assertNotNull(created.getTraceParent(), "OutboxEvent should persist W3C traceparent context");
        assertTrue(created.getTraceParent().startsWith("00-" + incomingTraceId + "-"),
                "OutboxEvent traceparent must preserve exact W3C traceId: actual=" + created.getTraceParent());

        int dispatched = outboxProcessor.processOutboxEvents();
        assertTrue(dispatched >= 1, "Outbox worker should process traced event");

        OutboxEvent reloaded = outboxEventRepository.findById(created.getId()).orElseThrow();
        assertEquals(OutboxEvent.OutboxStatus.PROCESSED, reloaded.getStatus());
    }

    @Test
    @DisplayName("TraceSanitizer enforces W3C format, PII redaction, and low-cardinality reason normalization")
    void traceSanitizer_RedactsSensitiveValuesAndNormalizesLabels() {
        assertNull(TraceSanitizer.sanitizeTraceParent("not-a-valid-header"));
        assertNull(TraceSanitizer.sanitizeTraceParent("00-00000000000000000000000000000000-00f067aa0ba902b7-01"));
        assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                TraceSanitizer.sanitizeTraceParent(" 00-4BF92F3577B34DA6A3CE929D0E0E4736-00F067AA0BA902B7-01 "));

        assertEquals("[REDACTED]", TraceSanitizer.redactAttribute("authorization", "Bearer eyJhbGci..."));
        assertEquals("[REDACTED]", TraceSanitizer.redactAttribute("userPhone", "0987654321"));
        assertEquals("[REDACTED]", TraceSanitizer.redactAttribute("apiSecret", "supersecret"));
        assertEquals("CASH", TraceSanitizer.redactAttribute("payment.method", "CASH"));

        assertEquals("INSUFFICIENT_CASH", TraceSanitizer.normalizeReasonCode("Khách đưa không đủ tiền"));
        assertEquals("INSUFFICIENT_STOCK", TraceSanitizer.normalizeReasonCode("Sản phẩm hết tồn kho"));
        assertEquals("NO_OPENED_SHIFT", TraceSanitizer.normalizeReasonCode("NO_OPENED_SHIFT"));
    }
}
