package com.possystem.backend.common.cache;

import com.possystem.backend.common.observability.PosTracingHelper;
import io.micrometer.tracing.Span;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Unified cache access service providing strongly typed read-through caching
 * with automatic stampede protection, graceful Redis failure fallback, and null-safe execution.
 */
@Slf4j
@Service
public class PosCacheService {

    private final ObjectProvider<CacheManager> cacheManagerProvider;
    private final CacheProperties cacheProperties;
    private final ObjectProvider<PosTracingHelper> tracingHelperProvider;

    public PosCacheService(ObjectProvider<CacheManager> cacheManagerProvider, CacheProperties cacheProperties) {
        this(cacheManagerProvider, cacheProperties, null);
    }

    @Autowired
    public PosCacheService(
            ObjectProvider<CacheManager> cacheManagerProvider,
            CacheProperties cacheProperties,
            ObjectProvider<PosTracingHelper> tracingHelperProvider) {
        this.cacheManagerProvider = cacheManagerProvider;
        this.cacheProperties = cacheProperties;
        this.tracingHelperProvider = tracingHelperProvider;
    }

    /**
     * Retrieves an entry from Redis cache or computes it atomically from the database loader.
     * Falls back cleanly to direct execution if CacheManager is absent or Redis is down.
     */
    public <T> T getOrLoad(String cacheName, String key, Supplier<T> dbLoader) {
        if (!cacheProperties.isEnabled()) {
            return dbLoader.get();
        }
        CacheManager cacheManager = cacheManagerProvider.getIfAvailable();
        if (cacheManager == null) {
            return dbLoader.get();
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return dbLoader.get();
        }
        PosTracingHelper tracingHelper = tracingHelperProvider != null ? tracingHelperProvider.getIfAvailable() : null;
        if (tracingHelper != null) {
            return tracingHelper.traceSupplier(
                    "pos.redis.cache",
                    Span.Kind.CLIENT,
                    null,
                    Map.of("cache.name", cacheName != null ? cacheName : "unknown"),
                    () -> cache.get(key, dbLoader::get));
        }
        return cache.get(key, dbLoader::get);
    }

    public void evictKey(String cacheName, String key) {
        CacheManager cacheManager = cacheManagerProvider.getIfAvailable();
        if (cacheManager == null) {
            return;
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    public void evictAll(String cacheName) {
        CacheManager cacheManager = cacheManagerProvider.getIfAvailable();
        if (cacheManager == null) {
            return;
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
