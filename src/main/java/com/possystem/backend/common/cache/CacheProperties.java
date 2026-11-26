package com.possystem.backend.common.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed configuration properties for Redis cache behavior, circuit breaker, and TTL policies.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {
    private boolean enabled = true;
    private long circuitBreakerCooldownMs = 10_000L;
    private Ttl ttl = new Ttl();

    @Data
    public static class Ttl {
        private long productsListSeconds = 180L;
        private long productsDetailSeconds = 300L;
        private long productsStatsSeconds = 300L;
        private long categoriesSeconds = 1800L;
        private long suppliersSeconds = 600L;
        private long discountsSeconds = 120L;
        private long dashboardSeconds = 60L;
        private long reportsCurrentSeconds = 120L;
        private long reportsHistoricalSeconds = 3600L;
    }
}
