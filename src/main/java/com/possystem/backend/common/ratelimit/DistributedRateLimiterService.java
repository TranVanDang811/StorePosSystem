package com.possystem.backend.common.ratelimit;

import com.possystem.backend.common.cache.RedisCircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distributed Token Bucket rate limiter backed by an atomic Redis Lua script.
 * Tracks quota dimensions across User ID, IP Address, and API Endpoint tier.
 * Automatically falls back to an in-memory Token Bucket if Redis is unavailable or circuit is OPEN.
 */
@Slf4j
@Service
public class DistributedRateLimiterService {

    private static final String TOKEN_BUCKET_LUA = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_per_ms = tonumber(ARGV[2])
            local requested = tonumber(ARGV[3])
            local ttl_sec = tonumber(ARGV[4])

            local t = redis.call('TIME')
            local now_ms = (tonumber(t[1]) * 1000) + math.floor(tonumber(t[2]) / 1000)

            local data = redis.call('HMGET', key, 'tokens', 'last_refill')
            local tokens = tonumber(data[1])
            local last_refill = tonumber(data[2])

            if tokens == nil or last_refill == nil then
                tokens = capacity
                last_refill = now_ms
            else
                local elapsed = math.max(0, now_ms - last_refill)
                tokens = math.min(capacity, tokens + (elapsed * refill_per_ms))
                last_refill = now_ms
            end

            local allowed = 0
            local retry_after = 0
            if tokens >= requested then
                tokens = tokens - requested
                allowed = 1
            else
                local deficit = requested - tokens
                if refill_per_ms > 0 then
                    retry_after = math.ceil(deficit / (refill_per_ms * 1000))
                else
                    retry_after = ttl_sec
                end
            end

            redis.call('HMSET', key, 'tokens', tostring(tokens), 'last_refill', tostring(last_refill))
            redis.call('EXPIRE', key, ttl_sec)

            return { allowed, math.floor(tokens), retry_after }
            """;

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final RateLimitProperties properties;
    private final RedisCircuitBreaker circuitBreaker;
    private final DefaultRedisScript<List> tokenBucketScript;
    private final ConcurrentHashMap<String, LocalTokenBucket> localBuckets = new ConcurrentHashMap<>();

    public DistributedRateLimiterService(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            RateLimitProperties properties,
            RedisCircuitBreaker circuitBreaker) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.properties = properties;
        this.circuitBreaker = circuitBreaker;
        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setScriptText(TOKEN_BUCKET_LUA);
        this.tokenBucketScript.setResultType(List.class);
    }

    /**
     * Enforces Token Bucket limits across User ID (when authenticated), IP Address, and Endpoint Tier.
     * Authenticated requests must pass both per-user quota and per-IP burst ceiling.
     */
    public RateLimitResult checkRequest(String tier, String endpointKey, String userId, String clientIp, int capacityPerMinute) {
        if (!properties.isEnabled() || capacityPerMinute <= 0) {
            return RateLimitResult.builder()
                    .allowed(true)
                    .limit(capacityPerMinute)
                    .remaining(capacityPerMinute)
                    .retryAfterSeconds(0)
                    .mode("DISABLED")
                    .build();
        }

        double refillPerMs = capacityPerMinute / 60_000.0;
        String safeEndpoint = sanitizeKeyPart(endpointKey);
        String safeIp = sanitizeKeyPart(clientIp);

        boolean hasAuthenticatedUser = userId != null && !userId.isBlank() && !"anonymousUser".equalsIgnoreCase(userId);
        if (hasAuthenticatedUser) {
            String userKey = String.format("pos:rl:%s:%s:user:%s", tier, safeEndpoint, sanitizeKeyPart(userId));
            RateLimitResult userResult = consumeToken(userKey, capacityPerMinute, refillPerMs);
            if (!userResult.isAllowed()) {
                return userResult;
            }

            // Per-IP ceiling for multi-terminal POS stores sharing a single LAN NAT IP (2x per-user cap)
            int ipCeiling = Math.max(capacityPerMinute, capacityPerMinute * 2);
            String ipKey = String.format("pos:rl:%s:%s:ip:%s", tier, safeEndpoint, safeIp);
            RateLimitResult ipResult = consumeToken(ipKey, ipCeiling, ipCeiling / 60_000.0);
            if (!ipResult.isAllowed()) {
                return ipResult;
            }
            return userResult;
        }

        // Unauthenticated request: enforce strict per-IP limit
        String ipKey = String.format("pos:rl:%s:%s:ip:%s", tier, safeEndpoint, safeIp);
        return consumeToken(ipKey, capacityPerMinute, refillPerMs);
    }

    /**
     * Atomically consumes 1 token from a specific rate limit bucket key.
     */
    public RateLimitResult consumeToken(String bucketKey, int capacity, double refillPerMs) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (properties.isRedisEnabled() && redisTemplate != null && circuitBreaker.allowRequest()) {
            try {
                List<?> raw = redisTemplate.execute(
                        tokenBucketScript,
                        List.of(bucketKey),
                        String.valueOf(capacity),
                        String.format(Locale.US, "%.8f", refillPerMs),
                        "1",
                        "120"
                );
                if (raw != null && raw.size() >= 3) {
                    circuitBreaker.recordSuccess();
                    long allowed = toLong(raw.get(0), 1L);
                    long remaining = toLong(raw.get(1), 0L);
                    long retryAfter = toLong(raw.get(2), 0L);
                    return RateLimitResult.builder()
                            .allowed(allowed == 1L)
                            .limit(capacity)
                            .remaining(Math.max(0L, remaining))
                            .retryAfterSeconds(Math.max(0L, retryAfter))
                            .mode("REDIS_LUA")
                            .build();
                }
            } catch (Exception ex) {
                circuitBreaker.recordFailure(ex);
                log.debug("Redis rate limiter fallback to local token bucket for key {}: {}", bucketKey, ex.getMessage());
            }
        }

        return consumeLocalToken(bucketKey, capacity, refillPerMs);
    }

    private RateLimitResult consumeLocalToken(String bucketKey, int capacity, double refillPerMs) {
        if (localBuckets.size() > 5000) {
            long staleCutoff = System.currentTimeMillis() - 180_000L;
            localBuckets.entrySet().removeIf(e -> e.getValue().lastRefillMs < staleCutoff);
        }
        LocalTokenBucket bucket = localBuckets.computeIfAbsent(bucketKey, k -> new LocalTokenBucket(capacity));
        return bucket.tryConsume(capacity, refillPerMs);
    }

    public void resetLocalBuckets() {
        localBuckets.clear();
    }

    private long toLong(Object val, long fallback) {
        if (val instanceof Number num) {
            return num.longValue();
        }
        try {
            return val != null ? Long.parseLong(val.toString()) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String sanitizeKeyPart(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        return raw.trim().replaceAll("[^a-zA-Z0-9._:-]", "_");
    }

    private static class LocalTokenBucket {
        private double tokens;
        private long lastRefillMs;

        LocalTokenBucket(int capacity) {
            this.tokens = capacity;
            this.lastRefillMs = System.currentTimeMillis();
        }

        synchronized RateLimitResult tryConsume(int capacity, double refillPerMs) {
            long now = System.currentTimeMillis();
            long elapsed = Math.max(0L, now - lastRefillMs);
            tokens = Math.min(capacity, tokens + (elapsed * refillPerMs));
            lastRefillMs = now;

            if (tokens >= 1.0) {
                tokens -= 1.0;
                return RateLimitResult.builder()
                        .allowed(true)
                        .limit(capacity)
                        .remaining((long) Math.floor(tokens))
                        .retryAfterSeconds(0L)
                        .mode("LOCAL_FALLBACK")
                        .build();
            }

            double deficit = 1.0 - tokens;
            long retryAfter = refillPerMs > 0 ? (long) Math.ceil(deficit / (refillPerMs * 1000.0)) : 60L;
            return RateLimitResult.builder()
                    .allowed(false)
                    .limit(capacity)
                    .remaining(0L)
                    .retryAfterSeconds(Math.max(1L, retryAfter))
                    .mode("LOCAL_FALLBACK")
                    .build();
        }
    }
}
