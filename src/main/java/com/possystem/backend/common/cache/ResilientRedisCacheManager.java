package com.possystem.backend.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Production-hardened {@link RedisCacheManager} decorating every cache region with:
 * 1. Circuit breaker protection (fail-fast bypass when Redis is unavailable).
 * 2. Striped per-key synchronization on cache-miss loads to prevent cache stampede.
 * 3. Self-healing eviction when stale/incompatible JSON payloads fail deserialization.
 * 4. Real-time cache metrics recording.
 */
@Slf4j
public class ResilientRedisCacheManager extends RedisCacheManager {

    private static final int LOCK_STRIPES = 64;

    private final RedisCircuitBreaker circuitBreaker;
    private final CacheMetricsService metricsService;
    private final CacheProperties cacheProperties;
    private final Object[] stripedLocks;

    public ResilientRedisCacheManager(
            RedisCacheWriter cacheWriter,
            RedisCacheConfiguration defaultCacheConfiguration,
            Map<String, RedisCacheConfiguration> initialCacheConfigurations,
            RedisCircuitBreaker circuitBreaker,
            CacheMetricsService metricsService,
            CacheProperties cacheProperties) {
        super(cacheWriter, defaultCacheConfiguration, initialCacheConfigurations, true);
        this.circuitBreaker = circuitBreaker;
        this.metricsService = metricsService;
        this.cacheProperties = cacheProperties;
        this.stripedLocks = new Object[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            this.stripedLocks[i] = new Object();
        }
    }

    @Override
    @NonNull
    protected Cache decorateCache(@NonNull Cache cache) {
        return new ResilientCacheDecorator(super.decorateCache(cache));
    }

    private Object lockFor(String cacheName, Object key) {
        int hash = (cacheName + "::" + String.valueOf(key)).hashCode() & 0x7fffffff;
        return stripedLocks[hash % LOCK_STRIPES];
    }

    private class ResilientCacheDecorator implements Cache {
        private final Cache delegate;

        ResilientCacheDecorator(Cache delegate) {
            this.delegate = delegate;
        }

        @Override
        @NonNull
        public String getName() {
            return delegate.getName();
        }

        @Override
        @NonNull
        public Object getNativeCache() {
            return delegate.getNativeCache();
        }

        @Override
        @Nullable
        public ValueWrapper get(@NonNull Object key) {
            if (!cacheProperties.isEnabled() || !circuitBreaker.allowRequest()) {
                return null;
            }
            try {
                ValueWrapper wrapper = delegate.get(key);
                circuitBreaker.recordSuccess();
                if (wrapper != null) {
                    metricsService.recordHit(getName());
                } else {
                    metricsService.recordMiss(getName());
                }
                return wrapper;
            } catch (SerializationException ex) {
                log.warn("Stale/incompatible cache entry in '{}' for key '{}'. Self-healing by evicting. Cause: {}",
                        getName(), key, ex.getMessage());
                metricsService.recordError(getName());
                safeEvictInternal(key);
                return null;
            } catch (Exception ex) {
                metricsService.recordError(getName());
                circuitBreaker.recordFailure(ex);
                return null;
            }
        }

        @Override
        @Nullable
        @SuppressWarnings("unchecked")
        public <T> T get(@NonNull Object key, @Nullable Class<T> type) {
            ValueWrapper wrapper = get(key);
            if (wrapper == null) {
                return null;
            }
            Object val = wrapper.get();
            if (val == null) {
                return null;
            }
            if (type != null && !type.isInstance(val)) {
                log.warn("Type mismatch in cache '{}' for key '{}': expected {}, got {}. Evicting stale entry.",
                        getName(), key, type.getSimpleName(), val.getClass().getSimpleName());
                safeEvictInternal(key);
                return null;
            }
            return (T) val;
        }

        @Override
        @Nullable
        public <T> T get(@NonNull Object key, @NonNull Callable<T> valueLoader) {
            // 1. If caching is disabled or circuit is OPEN, go straight to DB without Redis blocking
            if (!cacheProperties.isEnabled() || !circuitBreaker.allowRequest()) {
                metricsService.recordFallback(getName());
                return invokeLoader(valueLoader);
            }

            // 2. Fast-path lock-free read
            ValueWrapper fastHit = tryReadOnly(key);
            if (fastHit != null) {
                metricsService.recordHit(getName());
                @SuppressWarnings("unchecked")
                T cached = (T) fastHit.get();
                return cached;
            }

            // 3. Striped per-key mutex to eliminate concurrent cache stampede
            synchronized (lockFor(getName(), key)) {
                if (cacheProperties.isEnabled() && circuitBreaker.allowRequest()) {
                    ValueWrapper doubleCheckHit = tryReadOnly(key);
                    if (doubleCheckHit != null) {
                        metricsService.recordHit(getName());
                        @SuppressWarnings("unchecked")
                        T cached = (T) doubleCheckHit.get();
                        return cached;
                    }
                }

                metricsService.recordMiss(getName());
                T loadedValue = invokeLoader(valueLoader);

                if (loadedValue != null && cacheProperties.isEnabled() && circuitBreaker.allowRequest()) {
                    put(key, loadedValue);
                }
                return loadedValue;
            }
        }

        @Nullable
        private ValueWrapper tryReadOnly(Object key) {
            try {
                ValueWrapper wrapper = delegate.get(key);
                circuitBreaker.recordSuccess();
                return wrapper;
            } catch (SerializationException ex) {
                log.warn("Deserialization failure in cache '{}' for key '{}'. Self-healing eviction.", getName(), key);
                metricsService.recordError(getName());
                safeEvictInternal(key);
                return null;
            } catch (Exception ex) {
                metricsService.recordError(getName());
                metricsService.recordFallback(getName());
                circuitBreaker.recordFailure(ex);
                return null;
            }
        }

        private <T> T invokeLoader(Callable<T> valueLoader) {
            try {
                return valueLoader.call();
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception ex) {
                throw new IllegalStateException("Cache loader failed for cache " + getName(), ex);
            }
        }

        @Override
        public void put(@NonNull Object key, @Nullable Object value) {
            if (!cacheProperties.isEnabled() || !circuitBreaker.allowRequest()) {
                return;
            }
            try {
                delegate.put(key, value);
                circuitBreaker.recordSuccess();
                metricsService.recordPut(getName());
            } catch (Exception ex) {
                metricsService.recordError(getName());
                circuitBreaker.recordFailure(ex);
            }
        }

        @Override
        @Nullable
        public ValueWrapper putIfAbsent(@NonNull Object key, @Nullable Object value) {
            if (!cacheProperties.isEnabled() || !circuitBreaker.allowRequest()) {
                return null;
            }
            try {
                ValueWrapper existing = delegate.putIfAbsent(key, value);
                circuitBreaker.recordSuccess();
                if (existing == null) {
                    metricsService.recordPut(getName());
                }
                return existing;
            } catch (Exception ex) {
                metricsService.recordError(getName());
                circuitBreaker.recordFailure(ex);
                return null;
            }
        }

        @Override
        public void evict(@NonNull Object key) {
            if (!cacheProperties.isEnabled() || !circuitBreaker.allowRequest()) {
                return;
            }
            try {
                delegate.evict(key);
                circuitBreaker.recordSuccess();
                metricsService.recordEviction(getName());
            } catch (Exception ex) {
                metricsService.recordError(getName());
                circuitBreaker.recordFailure(ex);
            }
        }

        @Override
        public void clear() {
            if (!cacheProperties.isEnabled() || !circuitBreaker.allowRequest()) {
                return;
            }
            try {
                delegate.clear();
                circuitBreaker.recordSuccess();
                metricsService.recordEviction(getName());
            } catch (Exception ex) {
                metricsService.recordError(getName());
                circuitBreaker.recordFailure(ex);
            }
        }

        private void safeEvictInternal(Object key) {
            try {
                delegate.evict(key);
            } catch (Exception ignored) {
            }
        }
    }
}
