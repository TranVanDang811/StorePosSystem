package com.possystem.backend.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.possystem.backend.category.dto.CategoryResponse;
import com.possystem.backend.category.dto.CategorySummaryResponse;
import com.possystem.backend.common.cache.*;
import com.possystem.backend.discount.dto.DiscountResponse;
import com.possystem.backend.product.dto.ProductResponse;
import com.possystem.backend.report.dto.DailyReportResponse;
import com.possystem.backend.report.dto.DashboardReportResponse;
import com.possystem.backend.report.dto.MonthlyReportResponse;
import com.possystem.backend.report.dto.ProductSalesReportResponse;
import com.possystem.backend.supplier.dto.SupplierResponse;
import com.possystem.backend.supplier.dto.SupplierSummaryResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configures Spring Data Redis caching, strongly typed JSON serialization per cache region,
 * explicit TTL policies, and resilient fallback handling via {@link ResilientRedisCacheManager}.
 */
@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    private final CacheProperties cacheProperties;
    private final RedisCircuitBreaker circuitBreaker;
    private final CacheMetricsService cacheMetricsService;

    public RedisConfig(
            CacheProperties cacheProperties,
            RedisCircuitBreaker circuitBreaker,
            CacheMetricsService cacheMetricsService) {
        this.cacheProperties = cacheProperties;
        this.circuitBreaker = circuitBreaker;
        this.cacheMetricsService = cacheMetricsService;
    }

    private ObjectMapper createRedisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.enable(DeserializationFeature.USE_LONG_FOR_INTS);
        return mapper;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(ObjectProvider<RedisConnectionFactory> connectionFactoryProvider) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        RedisConnectionFactory connectionFactory = connectionFactoryProvider.getIfAvailable();
        if (connectionFactory != null) {
            template.setConnectionFactory(connectionFactory);
        }
        ObjectMapper mapper = createRedisObjectMapper();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(mapper);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        if (connectionFactory != null) {
            template.afterPropertiesSet();
        }
        return template;
    }

    @Bean
    public CacheManager cacheManager(ObjectProvider<RedisConnectionFactory> connectionFactoryProvider) {
        RedisConnectionFactory connectionFactory = connectionFactoryProvider.getIfAvailable();
        if (connectionFactory == null) {
            // Safe no-op cache manager for stripped @WebMvcTest slices where RedisAutoConfiguration is disabled
            return new NoOpCacheManager();
        }

        ObjectMapper mapper = createRedisObjectMapper();
        var typeFactory = mapper.getTypeFactory();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(cacheProperties.getTtl().getProductsListSeconds()))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        var ttl = cacheProperties.getTtl();

        // Products (Read-heavy POS catalog; NEVER source of truth for checkout pricing/stock mutation)
        cacheConfigs.put(CacheNames.PRODUCTS_LIST, typedConfig(mapper,
                typeFactory.constructParametricType(CachedPage.class, ProductResponse.class),
                ttl.getProductsListSeconds()));
        cacheConfigs.put(CacheNames.PRODUCTS_DETAIL, typedConfig(mapper,
                typeFactory.constructType(ProductResponse.class),
                ttl.getProductsDetailSeconds()));
        cacheConfigs.put(CacheNames.PRODUCTS_STATS, typedConfig(mapper,
                typeFactory.constructMapType(LinkedHashMap.class, String.class, Object.class),
                ttl.getProductsStatsSeconds()));
        cacheConfigs.put(CacheNames.PRODUCTS_BY_SUPPLIER, typedConfig(mapper,
                typeFactory.constructCollectionType(ArrayList.class, ProductResponse.class),
                ttl.getProductsDetailSeconds()));

        // Categories
        cacheConfigs.put(CacheNames.CATEGORIES_LIST, typedConfig(mapper,
                typeFactory.constructParametricType(CachedPage.class, CategoryResponse.class),
                ttl.getCategoriesSeconds()));
        cacheConfigs.put(CacheNames.CATEGORIES_DETAIL, typedConfig(mapper,
                typeFactory.constructType(CategoryResponse.class),
                ttl.getCategoriesSeconds()));
        cacheConfigs.put(CacheNames.CATEGORIES_SUMMARY, typedConfig(mapper,
                typeFactory.constructType(CategorySummaryResponse.class),
                ttl.getCategoriesSeconds()));

        // Suppliers
        cacheConfigs.put(CacheNames.SUPPLIERS_LIST, typedConfig(mapper,
                typeFactory.constructParametricType(CachedPage.class, SupplierResponse.class),
                ttl.getSuppliersSeconds()));
        cacheConfigs.put(CacheNames.SUPPLIERS_DETAIL, typedConfig(mapper,
                typeFactory.constructType(SupplierResponse.class),
                ttl.getSuppliersSeconds()));
        cacheConfigs.put(CacheNames.SUPPLIERS_SUMMARY, typedConfig(mapper,
                typeFactory.constructType(SupplierSummaryResponse.class),
                ttl.getSuppliersSeconds()));

        // Discounts (Catalog listing only; applyDiscountCode always locks DB directly)
        cacheConfigs.put(CacheNames.DISCOUNTS_ACTIVE, typedConfig(mapper,
                typeFactory.constructCollectionType(ArrayList.class, DiscountResponse.class),
                ttl.getDiscountsSeconds()));
        cacheConfigs.put(CacheNames.DISCOUNTS_ALL, typedConfig(mapper,
                typeFactory.constructCollectionType(ArrayList.class, DiscountResponse.class),
                ttl.getDiscountsSeconds()));

        // Reports & Dashboard Summary
        cacheConfigs.put(CacheNames.REPORTS_DASHBOARD, typedConfig(mapper,
                typeFactory.constructType(DashboardReportResponse.class),
                ttl.getDashboardSeconds()));
        cacheConfigs.put(CacheNames.REPORTS_DAILY, typedConfig(mapper,
                typeFactory.constructType(DailyReportResponse.class),
                ttl.getReportsCurrentSeconds()));
        cacheConfigs.put(CacheNames.REPORTS_MONTHLY, typedConfig(mapper,
                typeFactory.constructType(MonthlyReportResponse.class),
                ttl.getReportsCurrentSeconds()));
        cacheConfigs.put(CacheNames.REPORTS_PRODUCT_SALES, typedConfig(mapper,
                typeFactory.constructCollectionType(ArrayList.class, ProductSalesReportResponse.class),
                ttl.getReportsCurrentSeconds()));

        return new ResilientRedisCacheManager(
                RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory),
                defaultConfig,
                cacheConfigs,
                circuitBreaker,
                cacheMetricsService,
                cacheProperties
        );
    }

    private <T> RedisCacheConfiguration typedConfig(ObjectMapper mapper, JavaType javaType, long ttlSeconds) {
        Jackson2JsonRedisSerializer<T> serializer = new Jackson2JsonRedisSerializer<>(mapper, javaType);
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(Math.max(1L, ttlSeconds)))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new ResilientCacheErrorHandler(circuitBreaker, cacheMetricsService);
    }
}
