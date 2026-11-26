package com.possystem.backend.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight fail-fast circuit breaker protecting business APIs when Redis is unavailable.
 * Once a connection/timeout failure occurs, Redis calls are short-circuited for a cooldown window
 * so request latency does not degrade waiting on repeated socket timeouts.
 */
@Slf4j
@Component
public class RedisCircuitBreaker {

    private static final int FAILURE_THRESHOLD = 2;

    private final CacheProperties cacheProperties;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong openUntilEpochMs = new AtomicLong(0L);

    public RedisCircuitBreaker(CacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }

    /**
     * Returns true when Redis calls are allowed (CLOSED or HALF-OPEN probe).
     */
    public boolean allowRequest() {
        long openUntil = openUntilEpochMs.get();
        if (openUntil == 0L) {
            return true;
        }
        if (System.currentTimeMillis() >= openUntil) {
            return true; // Half-open probe allowed
        }
        return false;
    }

    public boolean isOpen() {
        return !allowRequest();
    }

    public String getState() {
        long openUntil = openUntilEpochMs.get();
        if (openUntil == 0L) {
            return "CLOSED";
        }
        if (System.currentTimeMillis() >= openUntil) {
            return "HALF_OPEN";
        }
        return "OPEN";
    }

    public void recordSuccess() {
        long previousOpenUntil = openUntilEpochMs.getAndSet(0L);
        int previousFailures = consecutiveFailures.getAndSet(0);
        if (previousOpenUntil > 0L || previousFailures > 0) {
            log.info("Redis connectivity recovered. Circuit breaker reset to CLOSED.");
        }
    }

    public void recordFailure(Throwable cause) {
        int failures = consecutiveFailures.incrementAndGet();
        long now = System.currentTimeMillis();
        long currentOpenUntil = openUntilEpochMs.get();

        // If already open or reached failure threshold, open circuit for cooldown period
        if (failures >= FAILURE_THRESHOLD || currentOpenUntil > 0L) {
            long cooldown = Math.max(1000L, cacheProperties.getCircuitBreakerCooldownMs());
            long targetOpenUntil = now + cooldown;
            if (openUntilEpochMs.compareAndSet(currentOpenUntil, targetOpenUntil)) {
                log.warn("Redis circuit breaker OPEN for {} ms after {} failure(s). Falling back to MySQL DB. Cause: {}",
                        cooldown, failures, cause != null ? cause.getMessage() : "unknown");
            }
        }
    }

    public void reset() {
        consecutiveFailures.set(0);
        openUntilEpochMs.set(0L);
    }
}
