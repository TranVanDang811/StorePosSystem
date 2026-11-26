package com.possystem.backend.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Fallback {@link CacheErrorHandler} ensuring any Spring {@code @Cacheable} / {@code @CacheEvict}
 * operations degrade gracefully to database execution if Redis encounters an error.
 */
@Slf4j
public class ResilientCacheErrorHandler implements CacheErrorHandler {

    private final RedisCircuitBreaker circuitBreaker;
    private final CacheMetricsService metricsService;

    public ResilientCacheErrorHandler(RedisCircuitBreaker circuitBreaker, CacheMetricsService metricsService) {
        this.circuitBreaker = circuitBreaker;
        this.metricsService = metricsService;
    }

    @Override
    public void handleCacheGetError(@NonNull RuntimeException exception, @NonNull Cache cache, @NonNull Object key) {
        metricsService.recordError(cache.getName());
        metricsService.recordFallback(cache.getName());
        circuitBreaker.recordFailure(exception);
        log.warn("Redis cache GET fallback to DB [cache={}, key={}]: {}", cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCachePutError(@NonNull RuntimeException exception, @NonNull Cache cache, @NonNull Object key, @Nullable Object value) {
        metricsService.recordError(cache.getName());
        circuitBreaker.recordFailure(exception);
        log.warn("Redis cache PUT skipped [cache={}, key={}]: {}", cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(@NonNull RuntimeException exception, @NonNull Cache cache, @NonNull Object key) {
        metricsService.recordError(cache.getName());
        circuitBreaker.recordFailure(exception);
        log.warn("Redis cache EVICT skipped [cache={}, key={}]: {}", cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheClearError(@NonNull RuntimeException exception, @NonNull Cache cache) {
        metricsService.recordError(cache.getName());
        circuitBreaker.recordFailure(exception);
        log.warn("Redis cache CLEAR skipped [cache={}]: {}", cache.getName(), exception.getMessage());
    }
}
