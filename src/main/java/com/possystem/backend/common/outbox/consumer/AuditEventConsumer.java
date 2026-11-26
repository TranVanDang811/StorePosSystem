package com.possystem.backend.common.outbox.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.possystem.backend.common.outbox.entity.AuditEvent;
import com.possystem.backend.common.outbox.event.DomainEventEnvelope;
import com.possystem.backend.common.outbox.event.PosEventType;
import com.possystem.backend.common.outbox.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Persists an immutable compliance audit trail for every POS domain event.
 */
@Slf4j
@Order(1)
@Component
@RequiredArgsConstructor
public class AuditEventConsumer implements PosEventConsumer {

    private static final Set<String> SUPPORTED = Set.of(
            PosEventType.ORDER_CREATED,
            PosEventType.ORDER_COMPLETED,
            PosEventType.ORDER_CANCELLED,
            PosEventType.PAYMENT_COMPLETED,
            PosEventType.PAYMENT_FAILED,
            PosEventType.INVENTORY_UPDATED,
            PosEventType.VOUCHER_REDEEMED,
            PosEventType.LOYALTY_POINTS_EARNED,
            PosEventType.SHIFT_CLOSED,
            PosEventType.LEGACY_ORDER_CREATED,
            PosEventType.LEGACY_PAYMENT_SUCCESS
    );

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String consumerName() {
        return "AuditEventConsumer";
    }

    @Override
    public boolean supports(String eventType) {
        return eventType != null && (SUPPORTED.contains(eventType) || !eventType.isBlank());
    }

    @Override
    public void consume(DomainEventEnvelope envelope) {
        if (auditEventRepository.existsByEventId(envelope.getEventId())) {
            return;
        }

        Map<String, Object> payload = envelope.getPayload() != null ? envelope.getPayload() : Map.of();
        String actor = firstNonBlank(
                asString(payload.get("actor")),
                asString(payload.get("closedByName")),
                asString(payload.get("openedByName")),
                asString(payload.get("cashier")),
                asString(payload.get("userPhone")),
                "SYSTEM");

        String summary = buildSummary(envelope, payload);
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            payloadJson = payload.toString();
        }

        LocalDateTime now = LocalDateTime.now();
        AuditEvent audit = AuditEvent.builder()
                .eventId(envelope.getEventId())
                .eventType(envelope.getEventType())
                .aggregateType(envelope.getAggregateType())
                .aggregateId(envelope.getAggregateId())
                .correlationId(envelope.getCorrelationId())
                .actor(truncate(actor, 100))
                .summary(truncate(summary, 500))
                .payloadJson(payloadJson)
                .occurredAt(envelope.getOccurredAt() != null ? envelope.getOccurredAt() : now)
                .recordedAt(now)
                .build();

        auditEventRepository.save(audit);
        log.debug("Audit event recorded: {} [{}]", envelope.getEventType(), envelope.getAggregateId());
    }

    private String buildSummary(DomainEventEnvelope envelope, Map<String, Object> payload) {
        String eventType = envelope.getEventType();
        return switch (eventType) {
            case PosEventType.ORDER_CREATED, PosEventType.LEGACY_ORDER_CREATED ->
                    "Tạo đơn hàng " + firstNonBlank(asString(payload.get("orderCode")), envelope.getAggregateId());
            case PosEventType.ORDER_COMPLETED ->
                    "Hoàn tất đơn hàng " + firstNonBlank(asString(payload.get("orderCode")), envelope.getAggregateId());
            case PosEventType.ORDER_CANCELLED ->
                    "Hủy đơn hàng " + firstNonBlank(asString(payload.get("orderCode")), envelope.getAggregateId())
                            + " (" + firstNonBlank(asString(payload.get("reason")), "N/A") + ")";
            case PosEventType.PAYMENT_COMPLETED, PosEventType.LEGACY_PAYMENT_SUCCESS ->
                    "Thanh toán thành công " + firstNonBlank(asString(payload.get("amount")), "0")
                            + " (" + firstNonBlank(asString(payload.get("method")), "POS") + ")";
            case PosEventType.PAYMENT_FAILED ->
                    "Thanh toán thất bại đơn " + firstNonBlank(asString(payload.get("orderId")), envelope.getAggregateId())
                            + ": " + firstNonBlank(asString(payload.get("reason")), "Unknown");
            case PosEventType.INVENTORY_UPDATED ->
                    "Biến động kho [" + firstNonBlank(asString(payload.get("movementType")), "UPDATE") + "] "
                            + firstNonBlank(asString(payload.get("productName")), envelope.getAggregateId())
                            + " delta=" + firstNonBlank(asString(payload.get("quantityChange")), "0");
            case PosEventType.VOUCHER_REDEEMED ->
                    "Áp mã giảm giá " + firstNonBlank(asString(payload.get("discountCode")), envelope.getAggregateId());
            case PosEventType.LOYALTY_POINTS_EARNED ->
                    "Cộng " + firstNonBlank(asString(payload.get("pointsEarned")), "0") + " điểm thành viên";
            case PosEventType.SHIFT_CLOSED ->
                    "Kết ca " + firstNonBlank(asString(payload.get("shiftCode")), envelope.getAggregateId());
            default -> "Sự kiện " + eventType + " cho " + envelope.getAggregateId();
        };
    }

    private String asString(Object val) {
        return val != null ? String.valueOf(val) : null;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank() && !"null".equals(v)) {
                return v;
            }
        }
        return "";
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
