package com.possystem.backend.common.observability;

import com.possystem.backend.common.cache.CacheMetricsService;
import com.possystem.backend.common.cache.RedisCircuitBreaker;
import com.possystem.backend.common.outbox.entity.OutboxEvent;
import com.possystem.backend.common.outbox.kafka.KafkaPosEventConsumer;
import com.possystem.backend.common.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Exposes internal application infrastructure states (Redis Cache Hit Rate, Circuit Breaker,
 * Outbox backlog gauges, Kafka consumer counters) cleanly into Micrometer/Prometheus.
 */
@Slf4j
@Component
public class PosMetricsBinder implements MeterBinder {

    private static final long OUTBOX_REFRESH_INTERVAL_MS = 3_000L;

    private final ObjectProvider<CacheMetricsService> cacheMetricsProvider;
    private final ObjectProvider<RedisCircuitBreaker> circuitBreakerProvider;
    private final ObjectProvider<OutboxEventRepository> outboxRepoProvider;
    private final ObjectProvider<KafkaPosEventConsumer> kafkaConsumerProvider;

    private final EnumMap<OutboxEvent.OutboxStatus, AtomicLong> cachedOutboxCounts =
            new EnumMap<>(OutboxEvent.OutboxStatus.class);
    private final AtomicLong lastOutboxRefreshMs = new AtomicLong(0L);

    public PosMetricsBinder(
            ObjectProvider<CacheMetricsService> cacheMetricsProvider,
            ObjectProvider<RedisCircuitBreaker> circuitBreakerProvider,
            ObjectProvider<OutboxEventRepository> outboxRepoProvider,
            ObjectProvider<KafkaPosEventConsumer> kafkaConsumerProvider) {
        this.cacheMetricsProvider = cacheMetricsProvider;
        this.circuitBreakerProvider = circuitBreakerProvider;
        this.outboxRepoProvider = outboxRepoProvider;
        this.kafkaConsumerProvider = kafkaConsumerProvider;
        for (OutboxEvent.OutboxStatus status : OutboxEvent.OutboxStatus.values()) {
            cachedOutboxCounts.put(status, new AtomicLong(0L));
        }
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        CacheMetricsService cacheMetrics = cacheMetricsProvider.getIfAvailable();
        if (cacheMetrics != null) {
            FunctionCounter.builder("pos.cache.hits", cacheMetrics, CacheMetricsService::getTotalHits)
                    .description("Total Redis cache hits")
                    .register(registry);
            FunctionCounter.builder("pos.cache.misses", cacheMetrics, CacheMetricsService::getTotalMisses)
                    .description("Total Redis cache misses")
                    .register(registry);
            FunctionCounter.builder("pos.cache.puts", cacheMetrics, CacheMetricsService::getTotalPuts)
                    .description("Total Redis cache puts")
                    .register(registry);
            FunctionCounter.builder("pos.cache.evictions", cacheMetrics, CacheMetricsService::getTotalEvictions)
                    .description("Total Redis cache evictions")
                    .register(registry);
            FunctionCounter.builder("pos.cache.errors", cacheMetrics, CacheMetricsService::getTotalErrors)
                    .description("Total Redis cache errors")
                    .register(registry);
            FunctionCounter.builder("pos.cache.db_fallbacks", cacheMetrics, CacheMetricsService::getTotalDbFallbacks)
                    .description("Total cache fallbacks to database")
                    .register(registry);
            Gauge.builder("pos.cache.hit_rate.percent", cacheMetrics, CacheMetricsService::getHitRatePercent)
                    .description("Current Redis cache hit rate percentage (0-100)")
                    .register(registry);
        }

        RedisCircuitBreaker circuitBreaker = circuitBreakerProvider.getIfAvailable();
        if (circuitBreaker != null) {
            Gauge.builder("pos.cache.circuit_breaker.state", circuitBreaker, cb -> switch (cb.getState()) {
                        case "OPEN" -> 2.0;
                        case "HALF_OPEN" -> 1.0;
                        default -> 0.0;
                    })
                    .description("Redis circuit breaker state (0=CLOSED, 1=HALF_OPEN, 2=OPEN)")
                    .register(registry);
        }

        OutboxEventRepository outboxRepository = outboxRepoProvider.getIfAvailable();
        if (outboxRepository != null) {
            for (OutboxEvent.OutboxStatus status : OutboxEvent.OutboxStatus.values()) {
                Gauge.builder("pos.outbox.events.count", status, s -> getOutboxStatusCount(outboxRepository, s))
                        .description("Outbox event count by lifecycle status")
                        .tag("status", status.name())
                        .register(registry);
            }
        }

        KafkaPosEventConsumer kafkaConsumer = kafkaConsumerProvider.getIfAvailable();
        if (kafkaConsumer != null) {
            FunctionCounter.builder("pos.kafka.consumer.consumed.total", kafkaConsumer,
                            c -> (double) c.getConsumedCount())
                    .description("Total events successfully consumed from Kafka domain topics")
                    .register(registry);
            FunctionCounter.builder("pos.kafka.consumer.dlt.total", kafkaConsumer,
                            c -> (double) c.getDltCount())
                    .description("Total events routed to Kafka Dead Letter Topic")
                    .register(registry);
        }
    }

    private double getOutboxStatusCount(OutboxEventRepository repo, OutboxEvent.OutboxStatus status) {
        long now = System.currentTimeMillis();
        long prev = lastOutboxRefreshMs.get();
        if (now - prev >= OUTBOX_REFRESH_INTERVAL_MS && lastOutboxRefreshMs.compareAndSet(prev, now)) {
            refreshOutboxSnapshot(repo);
        }
        return cachedOutboxCounts.get(status).get();
    }

    private void refreshOutboxSnapshot(OutboxEventRepository repo) {
        try {
            for (Map.Entry<OutboxEvent.OutboxStatus, AtomicLong> entry : cachedOutboxCounts.entrySet()) {
                entry.getValue().set(repo.countByStatus(entry.getKey()));
            }
        } catch (Exception ex) {
            log.debug("Could not refresh outbox gauge snapshot: {}", ex.getMessage());
        }
    }
}
