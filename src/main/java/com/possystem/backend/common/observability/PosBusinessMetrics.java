package com.possystem.backend.common.observability;

import com.possystem.backend.common.enums.PaymentMethod;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/**
 * Publishes low-cardinality, PII-free POS business metrics to Micrometer/Prometheus.
 * Zero personal information (phone, username, JWT, customer name, raw messages) is used in tags.
 */
@Slf4j
@Component
public class PosBusinessMetrics {

    private final MeterRegistry meterRegistry;

    public PosBusinessMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordOrderCreated(BigDecimal finalAmount) {
        Counter.builder("pos.orders.placed")
                .description("Total POS orders placed")
                .tag("outcome", "CREATED")
                .register(meterRegistry)
                .increment();

        if (finalAmount != null && finalAmount.signum() > 0) {
            DistributionSummary.builder("pos.orders.amount")
                    .description("Distribution of POS order final amounts in VND")
                    .baseUnit("VND")
                    .register(meterRegistry)
                    .record(finalAmount.doubleValue());
        }
    }

    public void recordOrderCancelled(String rawReason) {
        String reason = TraceSanitizer.normalizeReasonCode(rawReason);
        Counter.builder("pos.orders.cancelled")
                .description("Total POS orders cancelled")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    public void recordPaymentCompleted(PaymentMethod method, BigDecimal amount, String source) {
        String safeMethod = method != null ? method.name() : "UNKNOWN";
        String safeSource = TraceSanitizer.sanitizeBoundedLabel(source, "POS");

        Counter.builder("pos.payments.completed")
                .description("Total POS payments completed successfully")
                .tag("method", safeMethod)
                .tag("source", safeSource)
                .register(meterRegistry)
                .increment();

        if (amount != null && amount.signum() > 0) {
            DistributionSummary.builder("pos.payments.amount")
                    .description("Distribution of completed payment amounts in VND")
                    .baseUnit("VND")
                    .tag("method", safeMethod)
                    .register(meterRegistry)
                    .record(amount.doubleValue());
        }
    }

    public void recordPaymentFailed(PaymentMethod method, String rawReason, String source) {
        String safeMethod = method != null ? method.name() : "UNKNOWN";
        String safeReason = TraceSanitizer.normalizeReasonCode(rawReason);
        String safeSource = TraceSanitizer.sanitizeBoundedLabel(source, "POS");

        Counter.builder("pos.payments.failed")
                .description("Total POS payment attempts failed")
                .tag("method", safeMethod)
                .tag("reason", safeReason)
                .tag("source", safeSource)
                .register(meterRegistry)
                .increment();
    }

    public void recordInventoryAdjustment(String movementType, int delta) {
        if (delta == 0) {
            return;
        }
        String safeType = TraceSanitizer.sanitizeBoundedLabel(movementType, "ADJUSTMENT");
        String direction = delta > 0 ? "IN" : "OUT";

        Counter.builder("pos.inventory.adjustments")
                .description("Total inventory stock adjustment events")
                .tag("movement_type", safeType)
                .tag("direction", direction)
                .register(meterRegistry)
                .increment();

        Counter.builder("pos.inventory.units.moved")
                .description("Total stock units adjusted (absolute quantity)")
                .tag("movement_type", safeType)
                .tag("direction", direction)
                .register(meterRegistry)
                .increment(Math.abs(delta));
    }

    public void recordVoucherRedemption(String outcome, BigDecimal discountAmount) {
        String safeOutcome = TraceSanitizer.sanitizeBoundedLabel(outcome, "SUCCESS");
        Counter.builder("pos.vouchers.redemptions")
                .description("Total voucher redemption attempts")
                .tag("outcome", safeOutcome)
                .register(meterRegistry)
                .increment();

        if ("SUCCESS".equals(safeOutcome) && discountAmount != null && discountAmount.signum() > 0) {
            DistributionSummary.builder("pos.vouchers.discount.amount")
                    .description("Distribution of redeemed voucher discount value in VND")
                    .baseUnit("VND")
                    .register(meterRegistry)
                    .record(discountAmount.doubleValue());
        }
    }

    public void recordShiftOpened(String terminalId) {
        Counter.builder("pos.shifts.opened")
                .description("Total POS shifts opened")
                .tag("terminal", TraceSanitizer.sanitizeBoundedLabel(terminalId, "MAIN_COUNTER"))
                .register(meterRegistry)
                .increment();
    }

    public void recordShiftClosed(String terminalId, BigDecimal difference) {
        String safeTerminal = TraceSanitizer.sanitizeBoundedLabel(terminalId, "MAIN_COUNTER");
        Counter.builder("pos.shifts.closed")
                .description("Total POS shifts closed")
                .tag("terminal", safeTerminal)
                .register(meterRegistry)
                .increment();

        if (difference != null) {
            DistributionSummary.builder("pos.shifts.variance.abs")
                    .description("Absolute shift closing cash variance in VND")
                    .baseUnit("VND")
                    .tag("terminal", safeTerminal)
                    .register(meterRegistry)
                    .record(difference.abs().doubleValue());
        }
    }

    public void recordExternalCall(String provider, String operation, String outcome, long durationNanos) {
        Timer.builder("pos.external.requests")
                .description("Latency and outcome of external provider integrations")
                .tag("provider", TraceSanitizer.sanitizeBoundedLabel(provider, "EXTERNAL"))
                .tag("operation", TraceSanitizer.sanitizeBoundedLabel(operation, "CALL"))
                .tag("outcome", TraceSanitizer.sanitizeBoundedLabel(outcome, "SUCCESS"))
                .register(meterRegistry)
                .record(Math.max(0L, durationNanos), TimeUnit.NANOSECONDS);
    }

    public void recordOutboxDispatch(String eventType, String transport, String outcome, long durationNanos) {
        Timer.builder("pos.outbox.dispatch")
                .description("Duration and outcome of Transactional Outbox event dispatch")
                .tag("event_type", TraceSanitizer.sanitizeBoundedLabel(eventType, "UNKNOWN"))
                .tag("transport", TraceSanitizer.sanitizeBoundedLabel(transport, "LOCAL"))
                .tag("outcome", TraceSanitizer.sanitizeBoundedLabel(outcome, "PROCESSED"))
                .register(meterRegistry)
                .record(Math.max(0L, durationNanos), TimeUnit.NANOSECONDS);
    }

    public void recordKafkaConsume(String topic, String outcome) {
        Counter.builder("pos.kafka.consume")
                .description("Total Kafka domain events processed or routed to DLT")
                .tag("topic", topic != null && !topic.isBlank() ? topic.trim() : "unknown")
                .tag("outcome", TraceSanitizer.sanitizeBoundedLabel(outcome, "SUCCESS"))
                .register(meterRegistry)
                .increment();
    }
}
