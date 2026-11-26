package com.possystem.backend.common.outbox.consumer;

import com.possystem.backend.common.outbox.entity.DailySalesProjection;
import com.possystem.backend.common.outbox.event.DomainEventEnvelope;
import com.possystem.backend.common.outbox.event.PosEventType;
import com.possystem.backend.common.outbox.repository.DailySalesProjectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Maintains an eventually consistent read-model projection (daily_sales_projection)
 * driven strictly by domain events and guarded by IdempotentConsumerExecutor.
 */
@Slf4j
@Order(5)
@Component
@RequiredArgsConstructor
public class AnalyticsProjectionConsumer implements PosEventConsumer {

    private static final Set<String> SUPPORTED = Set.of(
            PosEventType.ORDER_CREATED,
            PosEventType.ORDER_COMPLETED,
            PosEventType.ORDER_CANCELLED,
            PosEventType.PAYMENT_COMPLETED,
            PosEventType.PAYMENT_FAILED,
            PosEventType.VOUCHER_REDEEMED,
            PosEventType.LOYALTY_POINTS_EARNED,
            PosEventType.SHIFT_CLOSED,
            PosEventType.LEGACY_ORDER_CREATED,
            PosEventType.LEGACY_PAYMENT_SUCCESS
    );

    private final DailySalesProjectionRepository projectionRepository;

    @Override
    public String consumerName() {
        return "AnalyticsProjectionConsumer";
    }

    @Override
    public boolean supports(String eventType) {
        return eventType != null && SUPPORTED.contains(eventType);
    }

    @Override
    public void consume(DomainEventEnvelope envelope) {
        LocalDate metricDate = envelope.getOccurredAt() != null
                ? envelope.getOccurredAt().toLocalDate()
                : LocalDate.now();

        DailySalesProjection projection = projectionRepository.findByMetricDateWithLock(metricDate)
                .orElseGet(() -> projectionRepository.save(DailySalesProjection.builder()
                        .metricDate(metricDate)
                        .updatedAt(LocalDateTime.now())
                        .build()));

        Map<String, Object> payload = envelope.getPayload() != null ? envelope.getPayload() : Map.of();

        switch (envelope.getEventType()) {
            case PosEventType.ORDER_CREATED, PosEventType.LEGACY_ORDER_CREATED ->
                    projection.setOrdersCreated(projection.getOrdersCreated() + 1);

            case PosEventType.ORDER_COMPLETED ->
                    projection.setOrdersCompleted(projection.getOrdersCompleted() + 1);

            case PosEventType.ORDER_CANCELLED ->
                    projection.setOrdersCancelled(projection.getOrdersCancelled() + 1);

            case PosEventType.PAYMENT_COMPLETED, PosEventType.LEGACY_PAYMENT_SUCCESS -> {
                projection.setPaymentsCompleted(projection.getPaymentsCompleted() + 1);
                BigDecimal amt = toBigDecimal(payload.get("amount"));
                projection.setTotalRevenue(projection.getTotalRevenue().add(amt));
            }

            case PosEventType.PAYMENT_FAILED ->
                    projection.setPaymentsFailed(projection.getPaymentsFailed() + 1);

            case PosEventType.VOUCHER_REDEEMED -> {
                projection.setVouchersRedeemed(projection.getVouchersRedeemed() + 1);
                BigDecimal disc = toBigDecimal(payload.get("discountAmount"));
                projection.setTotalDiscount(projection.getTotalDiscount().add(disc));
            }

            case PosEventType.LOYALTY_POINTS_EARNED -> {
                long pts = toLong(payload.get("pointsEarned"));
                projection.setTotalLoyaltyPointsEarned(projection.getTotalLoyaltyPointsEarned() + pts);
            }

            case PosEventType.SHIFT_CLOSED ->
                    projection.setShiftsClosed(projection.getShiftsClosed() + 1);

            default -> {
                // Ignore unsupported events
            }
        }

        projection.setUpdatedAt(LocalDateTime.now());
        projectionRepository.save(projection);
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(val));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        try {
            return val != null ? Long.parseLong(String.valueOf(val)) : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
}
