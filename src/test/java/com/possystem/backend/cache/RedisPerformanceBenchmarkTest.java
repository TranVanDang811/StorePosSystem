package com.possystem.backend.cache;

import com.possystem.backend.category.entity.Category;
import com.possystem.backend.category.repository.CategoryRepository;
import com.possystem.backend.category.service.CategoryService;
import com.possystem.backend.common.cache.CacheInvalidationService;
import com.possystem.backend.common.cache.CacheMetricsService;
import com.possystem.backend.common.cache.CacheProperties;
import com.possystem.backend.common.enums.OrderStatus;
import com.possystem.backend.common.enums.PaymentMethod;
import com.possystem.backend.common.enums.PaymentStatus;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.common.outbox.service.OutboxProcessor;
import com.possystem.backend.order.entity.OrderDetail;
import com.possystem.backend.order.entity.Orders;
import com.possystem.backend.order.repository.OrderRepository;
import com.possystem.backend.payment.entity.Payment;
import com.possystem.backend.payment.repository.PaymentRepository;
import com.possystem.backend.product.dto.ProductFilterRequest;
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.product.service.ProductService;
import com.possystem.backend.report.service.ReportService;
import com.possystem.backend.supplier.entity.Supplier;
import com.possystem.backend.supplier.repository.SupplierRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproducible service + Hibernate + Redis performance benchmark comparing
 * uncached DB execution vs Redis warm cache (Latency Avg/P95/P99, Throughput, DB Query Count, Hit Rate).
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@ActiveProfiles("test")
class RedisPerformanceBenchmarkTest {

    // Disable background outbox polling during Hibernate statement counting benchmark
    @MockitoBean private OutboxProcessor outboxProcessor;

    @Autowired private ProductService productService;
    @Autowired private CategoryService categoryService;
    @Autowired private ReportService reportService;
    @Autowired private CacheProperties cacheProperties;
    @Autowired private CacheMetricsService cacheMetricsService;
    @Autowired private CacheInvalidationService cacheInvalidationService;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;

    private Statistics stats;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        if (productRepository.count() < 40) {
            seedBenchmarkDataset();
        }
        sampleProduct = productRepository.findAll().stream().findFirst().orElseThrow();
    }

    @AfterEach
    void tearDown() {
        cacheProperties.setEnabled(true);
    }

    private void seedBenchmarkDataset() {
        List<Category> categories = new ArrayList<>();
        for (int c = 1; c <= 5; c++) {
            final int catIdx = c;
            String catName = "BENCH_CAT_" + catIdx;
            categories.add(categoryRepository.findByName(catName)
                    .orElseGet(() -> categoryRepository.save(Category.builder()
                            .name(catName)
                            .description("Benchmark Category " + catIdx)
                            .build())));
        }

        Supplier supplier = supplierRepository.findByName("BENCH_SUPPLIER")
                .orElseGet(() -> supplierRepository.save(Supplier.builder()
                        .name("BENCH_SUPPLIER")
                        .phone("0909887766")
                        .build()));

        List<Product> products = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            String code = "BENCH-SKU-" + String.format("%03d", i);
            if (!productRepository.existsByProductCodeIgnoreCase(code)) {
                products.add(productRepository.save(Product.builder()
                        .productCode(code)
                        .name("Đồ uống Benchmark #" + i)
                        .price(BigDecimal.valueOf(25000 + (i * 1000L)))
                        .cost(BigDecimal.valueOf(10000))
                        .unit("Ly")
                        .stock(100)
                        .status(ProductStatus.ACTIVE)
                        .category(categories.get(i % categories.size()))
                        .supplier(supplier)
                        .build()));
            }
        }
        if (products.isEmpty()) {
            products = productRepository.findAll();
        }

        for (int o = 1; o <= 40; o++) {
            Product p = products.get(o % products.size());
            BigDecimal amount = p.getPrice().multiply(BigDecimal.valueOf(2));
            Orders order = Orders.builder()
                    .orderCode("ORD-BENCH-" + UUID.randomUUID().toString().substring(0, 8))
                    .status(OrderStatus.PAID)
                    .totalPrice(amount)
                    .discountAmount(BigDecimal.ZERO)
                    .finalAmount(amount)
                    .build();
            OrderDetail detail = OrderDetail.builder()
                    .order(order)
                    .product(p)
                    .quantity(2)
                    .price(p.getPrice())
                    .totalPrice(amount)
                    .build();
            order.setOrderDetails(Set.of(detail));
            order = orderRepository.save(order);

            paymentRepository.save(Payment.builder()
                    .order(order)
                    .amount(amount)
                    .method(o % 2 == 0 ? PaymentMethod.CASH : PaymentMethod.BANK_TRANSFER)
                    .status(PaymentStatus.SUCCESS)
                    .paymentDate(LocalDateTime.now())
                    .transactionId("TXN-BENCH-" + o + "-" + UUID.randomUUID().toString().substring(0, 6))
                    .build());
        }
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("Benchmark: Before vs After Redis Integration (Latency, P95, P99, Throughput, SQL Queries, Hit Rate)")
    void runBeforeAndAfterBenchmark() throws InterruptedException {
        ProductFilterRequest filterReq = ProductFilterRequest.builder().page(1).size(50).build();
        String productCode = sampleProduct.getProductCode();
        LocalDate today = LocalDate.now();
        int iterations = 150;

        // Warm up JVM/Hibernate/Security once
        cacheProperties.setEnabled(false);
        productService.filterProducts(filterReq);
        productService.getProductCode(productCode);
        categoryService.getAll(0, 50, null);
        reportService.getDashboardReport();
        reportService.generateDailyReport(today);

        System.out.println("\n================ PHASE 3 PERFORMANCE BENCHMARK (BEFORE vs AFTER REDIS) ================");
        System.out.printf("%-26s | %-10s | %7s | %8s | %8s | %9s | %8s | %8s%n",
                "Operation", "Mode", "Avg(ms)", "P95(ms)", "P99(ms)", "Tput(r/s)", "DB_Qry", "HitRate%");
        System.out.println("--------------------------------------------------------------------------------------------------------");

        BenchResult prodBefore = measureSequential("Product List (filter 50)", false, iterations, () -> productService.filterProducts(filterReq));
        BenchResult prodAfter  = measureSequential("Product List (filter 50)", true,  iterations, () -> productService.filterProducts(filterReq));

        BenchResult detBefore  = measureSequential("Product Detail (byCode)", false, iterations, () -> productService.getProductCode(productCode));
        BenchResult detAfter   = measureSequential("Product Detail (byCode)", true,  iterations, () -> productService.getProductCode(productCode));

        BenchResult catBefore  = measureSequential("Category List + Counts",  false, iterations, () -> categoryService.getAll(0, 50, null));
        BenchResult catAfter   = measureSequential("Category List + Counts",  true,  iterations, () -> categoryService.getAll(0, 50, null));

        BenchResult dashBefore = measureSequential("Dashboard Summary",       false, iterations, () -> reportService.getDashboardReport());
        BenchResult dashAfter  = measureSequential("Dashboard Summary",       true,  iterations, () -> reportService.getDashboardReport());

        BenchResult dayBefore  = measureSequential("Daily Sales Report",      false, iterations, () -> reportService.generateDailyReport(today));
        BenchResult dayAfter   = measureSequential("Daily Sales Report",      true,  iterations, () -> reportService.generateDailyReport(today));

        BenchResult concBefore = measureConcurrent("Dashboard 10-Thread Burst", false, 10, 10, () -> reportService.getDashboardReport());
        BenchResult concAfter  = measureConcurrent("Dashboard 10-Thread Burst", true,  10, 10, () -> reportService.getDashboardReport());
        System.out.println("========================================================================================================\n");

        assertEquals(0L, prodAfter.dbQueries, "Warm Product List must run 0 SQL queries");
        assertEquals(0L, detAfter.dbQueries, "Warm Product Detail must run 0 SQL queries");
        assertEquals(0L, catAfter.dbQueries, "Warm Category List must run 0 SQL queries");
        assertEquals(0L, dashAfter.dbQueries, "Warm Dashboard Summary must run 0 SQL queries");
        assertEquals(0L, dayAfter.dbQueries, "Warm Daily Report must run 0 SQL queries");
        assertEquals(0L, concAfter.dbQueries, "Warm Concurrent Dashboard burst must run 0 SQL queries");

        assertTrue(dashAfter.avgMs < dashBefore.avgMs, "Dashboard average latency with Redis must be lower than DB");
        assertTrue(prodAfter.avgMs < prodBefore.avgMs, "Product List latency with Redis must be lower than DB");
        assertTrue(concAfter.avgMs < concBefore.avgMs, "Concurrent Dashboard burst with Redis must outperform DB");
        assertTrue(dashAfter.hitRatePercent > 99.0, "Warm cache hit rate should be > 99%");
    }

    private BenchResult measureSequential(String name, boolean redisEnabled, int iterations, Runnable task) {
        cacheProperties.setEnabled(redisEnabled);
        cacheInvalidationService.evictAllCaches();
        cacheMetricsService.reset();

        if (redisEnabled) {
            task.run(); // prime warm cache
        }

        stats.clear();
        cacheMetricsService.reset();
        long[] latenciesNs = new long[iterations];
        long totalStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            long t0 = System.nanoTime();
            task.run();
            latenciesNs[i] = System.nanoTime() - t0;
        }
        long totalNs = System.nanoTime() - totalStart;
        long dbQueries = stats.getPrepareStatementCount();
        double hitRate = cacheMetricsService.getHitRatePercent();

        BenchResult result = computeResult(name, redisEnabled ? "REDIS_WARM" : "NO_REDIS", latenciesNs, totalNs, dbQueries, hitRate);
        printRow(result);
        return result;
    }

    private BenchResult measureConcurrent(String name, boolean redisEnabled, int threads, int callsPerThread, Runnable task) throws InterruptedException {
        cacheProperties.setEnabled(redisEnabled);
        cacheInvalidationService.evictAllCaches();
        if (redisEnabled) {
            task.run();
        }

        stats.clear();
        cacheMetricsService.reset();
        int totalCalls = threads * callsPerThread;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        SecurityContext ctx = SecurityContextHolder.getContext();
        long[] latenciesNs = new long[totalCalls];

        for (int t = 0; t < threads; t++) {
            final int threadIdx = t;
            pool.submit(() -> {
                SecurityContextHolder.setContext(ctx);
                ready.countDown();
                try {
                    start.await();
                    for (int k = 0; k < callsPerThread; k++) {
                        long t0 = System.nanoTime();
                        task.run();
                        latenciesNs[(threadIdx * callsPerThread) + k] = System.nanoTime() - t0;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        long totalStart = System.nanoTime();
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        long totalNs = System.nanoTime() - totalStart;
        pool.shutdown();

        BenchResult result = computeResult(name, redisEnabled ? "REDIS_WARM" : "NO_REDIS", latenciesNs, totalNs,
                stats.getPrepareStatementCount(), cacheMetricsService.getHitRatePercent());
        printRow(result);
        return result;
    }

    private BenchResult computeResult(String name, String mode, long[] latenciesNs, long totalNs, long dbQueries, double hitRate) {
        long[] sorted = Arrays.copyOf(latenciesNs, latenciesNs.length);
        Arrays.sort(sorted);

        double sumMs = 0.0;
        for (long ns : sorted) {
            sumMs += ns / 1_000_000.0;
        }
        double avgMs = sumMs / sorted.length;
        double p95Ms = sorted[Math.min(sorted.length - 1, (int) Math.floor(sorted.length * 0.95))] / 1_000_000.0;
        double p99Ms = sorted[Math.min(sorted.length - 1, (int) Math.floor(sorted.length * 0.99))] / 1_000_000.0;
        double throughput = sorted.length / (totalNs / 1_000_000_000.0);

        return new BenchResult(name, mode, avgMs, p95Ms, p99Ms, throughput, dbQueries, hitRate);
    }

    private void printRow(BenchResult r) {
        System.out.printf("%-26s | %-10s | %7.3f | %8.3f | %8.3f | %9.1f | %8d | %7.2f%%%n",
                r.name, r.mode, r.avgMs, r.p95Ms, r.p99Ms, r.throughput, r.dbQueries, r.hitRatePercent);
    }

    private record BenchResult(
            String name, String mode, double avgMs, double p95Ms, double p99Ms,
            double throughput, long dbQueries, double hitRatePercent) {}
}
