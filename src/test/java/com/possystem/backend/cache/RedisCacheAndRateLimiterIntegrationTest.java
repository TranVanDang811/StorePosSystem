package com.possystem.backend.cache;

import com.possystem.backend.category.dto.CategoryResponse;
import com.possystem.backend.category.dto.CategorySummaryResponse;
import com.possystem.backend.category.entity.Category;
import com.possystem.backend.category.repository.CategoryRepository;
import com.possystem.backend.category.service.CategoryService;
import com.possystem.backend.common.cache.CacheInvalidationService;
import com.possystem.backend.common.cache.CacheMetricsService;
import com.possystem.backend.common.cache.CacheNames;
import com.possystem.backend.common.cache.CachedPage;
import com.possystem.backend.common.cache.RedisCircuitBreaker;
import com.possystem.backend.common.enums.PaymentMethod;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.common.enums.ShiftStatus;
import com.possystem.backend.common.ratelimit.DistributedRateLimiterService;
import com.possystem.backend.common.ratelimit.RateLimitProperties;
import com.possystem.backend.common.ratelimit.RateLimitResult;
import com.possystem.backend.discount.dto.DiscountRequest;
import com.possystem.backend.discount.dto.DiscountResponse;
import com.possystem.backend.discount.service.DiscountService;
import com.possystem.backend.order.dto.OrderDetailRequest;
import com.possystem.backend.order.dto.OrderRequest;
import com.possystem.backend.order.dto.OrderResponse;
import com.possystem.backend.order.service.OrderService;
import com.possystem.backend.payment.dto.PaymentRequest;
import com.possystem.backend.payment.dto.PaymentResponse;
import com.possystem.backend.payment.service.PaymentService;
import com.possystem.backend.product.dto.ProductFilterRequest;
import com.possystem.backend.product.dto.ProductRequest;
import com.possystem.backend.product.dto.ProductResponse;
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.product.service.ProductService;
import com.possystem.backend.report.dto.DailyReportResponse;
import com.possystem.backend.report.dto.DashboardReportResponse;
import com.possystem.backend.report.dto.MonthlyReportResponse;
import com.possystem.backend.report.dto.ProductSalesReportResponse;
import com.possystem.backend.report.service.ReportService;
import com.possystem.backend.shift.entity.Shift;
import com.possystem.backend.shift.repository.ShiftRepository;
import com.possystem.backend.supplier.dto.SupplierResponse;
import com.possystem.backend.supplier.dto.SupplierSummaryResponse;
import com.possystem.backend.supplier.entity.Supplier;
import com.possystem.backend.supplier.repository.SupplierRepository;
import com.possystem.backend.supplier.service.SupplierService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RedisCacheAndRateLimiterIntegrationTest {

    @Autowired private ProductService productService;
    @Autowired private CategoryService categoryService;
    @Autowired private SupplierService supplierService;
    @Autowired private DiscountService discountService;
    @Autowired private ReportService reportService;
    @Autowired private OrderService orderService;
    @Autowired private PaymentService paymentService;

    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private ShiftRepository shiftRepository;

    @Autowired private CacheManager cacheManager;
    @Autowired private CacheMetricsService cacheMetricsService;
    @Autowired private CacheInvalidationService cacheInvalidationService;
    @Autowired private RedisCircuitBreaker circuitBreaker;
    @Autowired private StringRedisTemplate stringRedisTemplate;
    @Autowired private DistributedRateLimiterService rateLimiterService;
    @Autowired private RateLimitProperties rateLimitProperties;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private MockMvc mockMvc;

    private Statistics hibernateStats;
    private Category testCategory;
    private Supplier testSupplier;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        hibernateStats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        hibernateStats.setStatisticsEnabled(true);

        circuitBreaker.reset();
        cacheMetricsService.reset();
        rateLimiterService.resetLocalBuckets();
        cacheInvalidationService.evictAllCaches();

        // Clean rate-limit keys created by previous tests
        Set<String> rlKeys = stringRedisTemplate.keys("pos:rl:*");
        if (rlKeys != null && !rlKeys.isEmpty()) {
            stringRedisTemplate.delete(rlKeys);
        }

        if (shiftRepository.findByStatus(ShiftStatus.OPEN).isEmpty()) {
            shiftRepository.save(Shift.builder()
                    .shiftCode("SHIFT-REDIS-001")
                    .startTime(LocalDateTime.now())
                    .openingCash(BigDecimal.valueOf(1000000))
                    .status(ShiftStatus.OPEN)
                    .terminalId("MAIN_COUNTER")
                    .build());
        }

        testCategory = categoryRepository.findByName("REDIS_COFFEE")
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name("REDIS_COFFEE")
                        .description("Specialty Coffee")
                        .build()));

        testSupplier = supplierRepository.findByName("REDIS_SUPPLIER")
                .orElseGet(() -> supplierRepository.save(Supplier.builder()
                        .name("REDIS_SUPPLIER")
                        .phone("0911223344")
                        .email("supplier@dangcap.vn")
                        .build()));

        String uniqueCode = "PROD-RD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        testProduct = productRepository.save(Product.builder()
                .productCode(uniqueCode)
                .name("Bạc Xỉu Đẳng Cấp")
                .price(BigDecimal.valueOf(35000))
                .cost(BigDecimal.valueOf(12000))
                .unit("Ly")
                .stock(20)
                .status(ProductStatus.ACTIVE)
                .category(testCategory)
                .supplier(testSupplier)
                .build());

        cacheInvalidationService.evictAllCaches();
        cacheMetricsService.reset();
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("Redis 1: Cache Hit/Miss & Zero DB Queries on Warm Reads (Catalog, Category, Supplier, Discount)")
    void testCatalogCaching_ZeroDbQueriesOnCacheHit() {
        ProductFilterRequest filter = ProductFilterRequest.builder()
                .page(1)
                .size(50)
                .categoryName("REDIS_COFFEE")
                .build();

        // 1. Product List - Cold miss
        hibernateStats.clear();
        Page<ProductResponse> firstPage = productService.filterProducts(filter);
        long coldQueries = hibernateStats.getPrepareStatementCount();
        assertTrue(coldQueries > 0, "Cold read must query DB");
        assertTrue(firstPage.getTotalElements() >= 1);

        // 2. Product List - Warm Redis Hit (0 SQL queries!)
        hibernateStats.clear();
        Page<ProductResponse> cachedPage = productService.filterProducts(filter);
        long warmQueries = hibernateStats.getPrepareStatementCount();
        assertEquals(0L, warmQueries, "Warm Redis read must execute 0 SQL queries");
        assertEquals(firstPage.getTotalElements(), cachedPage.getTotalElements());
        assertEquals(firstPage.getContent().get(0).getProductCode(), cachedPage.getContent().get(0).getProductCode());

        // 3. Product Detail by Code
        hibernateStats.clear();
        ProductResponse detailMiss = productService.getProductCode(testProduct.getProductCode());
        assertTrue(hibernateStats.getPrepareStatementCount() > 0);
        hibernateStats.clear();
        ProductResponse detailHit = productService.getProductCode(testProduct.getProductCode());
        assertEquals(0L, hibernateStats.getPrepareStatementCount());
        assertEquals(detailMiss.getName(), detailHit.getName());

        // 4. Product Statistics (Map<String, Object>)
        productService.getProductStatistics();
        hibernateStats.clear();
        Map<String, Object> cachedStats = productService.getProductStatistics();
        assertEquals(0L, hibernateStats.getPrepareStatementCount());
        assertTrue(((Number) cachedStats.get("totalProducts")).longValue() >= 1L);

        // 5. Category List + Summary
        categoryService.getAll(0, 20, null);
        categoryService.getSummary();
        hibernateStats.clear();
        Page<CategoryResponse> catCached = categoryService.getAll(0, 20, null);
        CategorySummaryResponse catSumCached = categoryService.getSummary();
        assertEquals(0L, hibernateStats.getPrepareStatementCount());
        assertTrue(catCached.getTotalElements() >= 1);
        assertTrue(catSumCached.getTotalCategories() >= 1);

        // 6. Supplier List + Summary
        supplierService.getAll(0, 20, null);
        supplierService.getSummary();
        hibernateStats.clear();
        Page<SupplierResponse> supCached = supplierService.getAll(0, 20, null);
        SupplierSummaryResponse supSumCached = supplierService.getSummary();
        assertEquals(0L, hibernateStats.getPrepareStatementCount());
        assertTrue(supCached.getTotalElements() >= 1);
        assertTrue(supSumCached.getTotalSuppliers() >= 1);

        // 7. Active Discounts
        discountService.createDiscount(DiscountRequest.builder()
                .name("Redis Test Promo")
                .code("RD-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .value(BigDecimal.valueOf(5000))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(3))
                .active(true)
                .usageLimit(100)
                .build());
        List<DiscountResponse> discCold = discountService.getActiveDiscounts();
        hibernateStats.clear();
        List<DiscountResponse> discHit = discountService.getActiveDiscounts();
        assertEquals(0L, hibernateStats.getPrepareStatementCount());
        assertEquals(discCold.size(), discHit.size());

        assertTrue(cacheMetricsService.getTotalHits() >= 7);
        assertEquals("CLOSED", circuitBreaker.getState());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("Redis 2: Dashboard Summary & Heavy Reports Serialization Round-Trip + 0 Queries on Hit")
    void testReportAndDashboardCaching_RoundTrip() {
        // Generate real order & payment so reports have real rows + top products + recent orders
        OrderResponse order = orderService.createOrder(OrderRequest.builder()
                .orderDetails(List.of(OrderDetailRequest.builder()
                        .productId(testProduct.getId())
                        .quantity(2)
                        .build()))
                .build());

        paymentService.createPayment(PaymentRequest.builder()
                .orderId(order.getId())
                .method(PaymentMethod.CASH)
                .cashReceived(BigDecimal.valueOf(100000))
                .idempotencyKey("RD-PAY-" + UUID.randomUUID())
                .build());

        // Cold Dashboard + Reports
        DashboardReportResponse dashCold = reportService.getDashboardReport();
        DailyReportResponse dailyCold = reportService.generateDailyReport(LocalDate.now());
        MonthlyReportResponse monthlyCold = reportService.generateMonthlyReport(YearMonth.now());
        List<ProductSalesReportResponse> prodSalesCold = reportService.getProductReport(null, null, null, null);

        // Warm Reads -> MUST be served from Redis with 0 DB statements
        hibernateStats.clear();
        DashboardReportResponse dashHit = reportService.getDashboardReport();
        DailyReportResponse dailyHit = reportService.generateDailyReport(LocalDate.now());
        MonthlyReportResponse monthlyHit = reportService.generateMonthlyReport(YearMonth.now());
        List<ProductSalesReportResponse> prodSalesHit = reportService.getProductReport(null, null, null, null);

        assertEquals(0L, hibernateStats.getPrepareStatementCount(), "All warm dashboard & report reads must run 0 SQL queries");
        assertEquals(dashCold.getTodayOrders(), dashHit.getTodayOrders());
        assertEquals(0, dashCold.getTodayRevenue().compareTo(dashHit.getTodayRevenue()));
        assertEquals(7, dashHit.getRevenueLast7Days().size());
        assertEquals(dailyCold.getDate(), dailyHit.getDate());
        assertEquals(monthlyCold.getMonth(), monthlyHit.getMonth());
        assertEquals(prodSalesCold.size(), prodSalesHit.size());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("Redis 3: Post-Commit Invalidation & DB Source-of-Truth Protection for Pricing/Stock")
    void testCacheInvalidation_AndAuthoritativeDbTruth() {
        ProductFilterRequest filter = ProductFilterRequest.builder()
                .page(1)
                .size(50)
                .productCode(testProduct.getProductCode())
                .build();

        // Warm both Product List & Product Detail caches
        Page<ProductResponse> beforePage = productService.filterProducts(filter);
        assertEquals(0, BigDecimal.valueOf(35000).compareTo(beforePage.getContent().get(0).getPrice()));

        // Update product price -> must invalidate cache after commit
        productService.update(testProduct.getId(), ProductRequest.builder()
                .productCode(testProduct.getProductCode())
                .name("Bạc Xỉu Đẳng Cấp V2")
                .price(BigDecimal.valueOf(45000))
                .cost(BigDecimal.valueOf(15000))
                .unit("Ly")
                .stock(BigDecimal.valueOf(20))
                .categoryName(testCategory.getName())
                .supplierName(testSupplier.getName())
                .build(), null);

        Page<ProductResponse> afterUpdate = productService.filterProducts(filter);
        assertEquals(0, BigDecimal.valueOf(45000).compareTo(afterUpdate.getContent().get(0).getPrice()));
        assertEquals("Bạc Xỉu Đẳng Cấp V2", productService.getProductCode(testProduct.getProductCode()).getName());

        // Inject FAKE stale price (1 VND) & stock (999) into Redis directly to simulate cache poisoning
        Cache detailCache = cacheManager.getCache(CacheNames.PRODUCTS_DETAIL);
        assertNotNull(detailCache);
        detailCache.put("code:" + testProduct.getProductCode().toUpperCase(), ProductResponse.builder()
                .id(testProduct.getId())
                .productCode(testProduct.getProductCode())
                .name("TAMPERED CACHE")
                .price(BigDecimal.ONE)
                .stock(BigDecimal.valueOf(999))
                .status(ProductStatus.ACTIVE)
                .build());

        // Authoritative Checkout MUST read real price (45000) & stock (20) from MySQL/H2, NOT Redis!
        OrderResponse order = orderService.createOrder(OrderRequest.builder()
                .orderDetails(List.of(OrderDetailRequest.builder()
                        .productId(testProduct.getId())
                        .quantity(3)
                        .build()))
                .build());
        assertEquals(0, BigDecimal.valueOf(135000).compareTo(order.getTotalPrice()),
                "Order pricing MUST use authoritative DB price (3 * 45000 = 135000), ignoring Redis");

        PaymentResponse payment = paymentService.createPayment(PaymentRequest.builder()
                .orderId(order.getId())
                .method(PaymentMethod.CASH)
                .cashReceived(BigDecimal.valueOf(150000))
                .idempotencyKey("AUTH-TRUTH-" + UUID.randomUUID())
                .build());
        assertEquals(0, BigDecimal.valueOf(135000).compareTo(payment.getAmount()));

        // After payment commit, cache is invalidated -> next catalog read reflects live DB stock (20 - 3 = 17)
        ProductResponse freshDetail = productService.getProductCode(testProduct.getProductCode());
        assertEquals(17, freshDetail.getStock().intValue());
        assertEquals(0, BigDecimal.valueOf(45000).compareTo(freshDetail.getPrice()));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("Redis 4: TTL Assignment, Self-Healing Corrupt Entry & Concurrent Stampede Protection")
    void testTtlSelfHealingAndStampedeProtection() throws InterruptedException {
        // 1. Verify Redis TTL is set cleanly
        productService.getProductCode(testProduct.getProductCode());
        String redisKey = CacheNames.PRODUCTS_DETAIL + "::code:" + testProduct.getProductCode().toUpperCase();
        Long ttl = stringRedisTemplate.getExpire(redisKey);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 300, "Redis key must have positive TTL <= 300s, got: " + ttl);

        // 2. Corrupt JSON in Redis -> Self-healing fallback to DB without exception
        stringRedisTemplate.opsForValue().set(redisKey, "{CORRUPTED_STALE_JSON_SCHEMA");
        ProductResponse healed = productService.getProductCode(testProduct.getProductCode());
        assertNotNull(healed);
        assertEquals(testProduct.getProductCode(), healed.getProductCode());

        // 3. Concurrent Stampede Protection on Cold Dashboard
        cacheInvalidationService.evictAllCaches();
        cacheMetricsService.reset();
        hibernateStats.clear();

        int threads = 12;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        SecurityContext secCtx = SecurityContextHolder.getContext();
        List<DashboardReportResponse> results = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                SecurityContextHolder.setContext(secCtx);
                try {
                    startLatch.await();
                    results.add(reportService.getDashboardReport());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(threads, results.size());
        assertTrue(cacheMetricsService.getTotalHits() >= threads - 1,
                "Striped stampede guard should yield >= " + (threads - 1) + " hits, got " + cacheMetricsService.getTotalHits());
    }

    @Test
    @DisplayName("Redis 5: Atomic Lua Distributed Rate Limiter (Concurrency, User ID vs IP, HTTP 429)")
    void testDistributedLuaRateLimiter_AtomicAndMultiDimension() throws Exception {
        // 1. Concurrency Atomicity: 25 threads racing for 10 Lua Token Bucket tokens -> EXACTLY 10 allowed
        String atomicKey = "pos:rl:test:atomic:" + UUID.randomUUID();
        int capacity = 10;
        int racers = 25;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(racers);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);

        for (int i = 0; i < racers; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    RateLimitResult res = rateLimiterService.consumeToken(atomicKey, capacity, 0.0);
                    assertEquals("REDIS_LUA", res.getMode());
                    if (res.isAllowed()) {
                        allowedCount.incrementAndGet();
                    } else {
                        blockedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        go.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(10, allowedCount.get(), "Atomic Lua script must allow exact capacity");
        assertEquals(15, blockedCount.get(), "Atomic Lua script must block remaining requests");

        // 2. User ID isolation on shared IP (cashierA exhausted -> cashierB still allowed)
        String sharedIp = "203.0.113.50";
        for (int i = 0; i < 15; i++) {
            RateLimitResult resA = rateLimiterService.checkRequest("VOUCHER_DISCOUNT", "voucher", "cashierA", sharedIp, 15);
            assertTrue(resA.isAllowed());
        }
        RateLimitResult blockedA = rateLimiterService.checkRequest("VOUCHER_DISCOUNT", "voucher", "cashierA", sharedIp, 15);
        assertFalse(blockedA.isAllowed());

        RateLimitResult allowedB = rateLimiterService.checkRequest("VOUCHER_DISCOUNT", "voucher", "cashierB", sharedIp, 15);
        assertTrue(allowedB.isAllowed(), "Separate User ID on same NAT IP must have independent quota");

        // 3. HTTP Endpoint Integration via MockMvc Filter Chain (/auth/token brute-force protection -> 429)
        int authLimit = rateLimitProperties.getAuthPerMinute();
        String testIp = "10.99.88.77";
        for (int i = 0; i < authLimit; i++) {
            mockMvc.perform(post("/auth/token")
                            .with(req -> {
                                req.setRemoteAddr(testIp);
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"unknown_rl\",\"password\":\"wrong\"}"))
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"));
        }

        mockMvc.perform(post("/auth/token")
                        .with(req -> {
                            req.setRemoteAddr(testIp);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"unknown_rl\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value(6001));
    }
}
