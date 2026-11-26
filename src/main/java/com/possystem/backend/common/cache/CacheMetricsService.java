package com.possystem.backend.common.cache;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks real-time Redis cache hits, misses, writes, evictions, errors, and DB fallbacks.
 */
@Service
public class CacheMetricsService {

    private final AtomicLong totalHits = new AtomicLong(0);
    private final AtomicLong totalMisses = new AtomicLong(0);
    private final AtomicLong totalPuts = new AtomicLong(0);
    private final AtomicLong totalEvictions = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong totalDbFallbacks = new AtomicLong(0);

    private final ConcurrentHashMap<String, CacheCounters> perCache = new ConcurrentHashMap<>();

    public void recordHit(String cacheName) {
        totalHits.incrementAndGet();
        countersFor(cacheName).hits.incrementAndGet();
    }

    public void recordMiss(String cacheName) {
        totalMisses.incrementAndGet();
        countersFor(cacheName).misses.incrementAndGet();
    }

    public void recordPut(String cacheName) {
        totalPuts.incrementAndGet();
        countersFor(cacheName).puts.incrementAndGet();
    }

    public void recordEviction(String cacheName) {
        totalEvictions.incrementAndGet();
        countersFor(cacheName).evictions.incrementAndGet();
    }

    public void recordError(String cacheName) {
        totalErrors.incrementAndGet();
        countersFor(cacheName).errors.incrementAndGet();
    }

    public void recordFallback(String cacheName) {
        totalDbFallbacks.incrementAndGet();
        countersFor(cacheName).fallbacks.incrementAndGet();
    }

    public double getHitRatePercent() {
        long hits = totalHits.get();
        long misses = totalMisses.get();
        long total = hits + misses;
        if (total == 0L) {
            return 0.0;
        }
        return (hits * 100.0) / total;
    }

    public long getTotalHits() {
        return totalHits.get();
    }

    public long getTotalMisses() {
        return totalMisses.get();
    }

    public long getTotalPuts() {
        return totalPuts.get();
    }

    public long getTotalEvictions() {
        return totalEvictions.get();
    }

    public long getTotalErrors() {
        return totalErrors.get();
    }

    public long getTotalDbFallbacks() {
        return totalDbFallbacks.get();
    }

    public void reset() {
        totalHits.set(0);
        totalMisses.set(0);
        totalPuts.set(0);
        totalEvictions.set(0);
        totalErrors.set(0);
        totalDbFallbacks.set(0);
        perCache.clear();
    }

    public Map<String, Object> snapshot(RedisCircuitBreaker circuitBreaker) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("circuitState", circuitBreaker != null ? circuitBreaker.getState() : "UNKNOWN");
        result.put("totalHits", totalHits.get());
        result.put("totalMisses", totalMisses.get());
        result.put("hitRatePercent", Math.round(getHitRatePercent() * 100.0) / 100.0);
        result.put("totalPuts", totalPuts.get());
        result.put("totalEvictions", totalEvictions.get());
        result.put("totalErrors", totalErrors.get());
        result.put("totalDbFallbacks", totalDbFallbacks.get());

        Map<String, Map<String, Long>> cacheDetails = new LinkedHashMap<>();
        perCache.forEach((name, c) -> {
            Map<String, Long> m = new LinkedHashMap<>();
            m.put("hits", c.hits.get());
            m.put("misses", c.misses.get());
            m.put("puts", c.puts.get());
            m.put("evictions", c.evictions.get());
            m.put("errors", c.errors.get());
            m.put("fallbacks", c.fallbacks.get());
            cacheDetails.put(name, m);
        });
        result.put("caches", cacheDetails);
        return result;
    }

    private CacheCounters countersFor(String cacheName) {
        return perCache.computeIfAbsent(cacheName != null ? cacheName : "unknown", k -> new CacheCounters());
    }

    private static class CacheCounters {
        final AtomicLong hits = new AtomicLong(0);
        final AtomicLong misses = new AtomicLong(0);
        final AtomicLong puts = new AtomicLong(0);
        final AtomicLong evictions = new AtomicLong(0);
        final AtomicLong errors = new AtomicLong(0);
        final AtomicLong fallbacks = new AtomicLong(0);
    }
}
