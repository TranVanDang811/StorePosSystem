package com.possystem.backend.cache;

import com.possystem.backend.category.entity.Category;
import com.possystem.backend.category.repository.CategoryRepository;
import com.possystem.backend.category.service.CategoryService;
import com.possystem.backend.common.cache.CacheMetricsService;
import com.possystem.backend.common.cache.RedisCircuitBreaker;
import com.possystem.backend.common.enums.PaymentMethod;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.common.enums.ShiftStatus;
import com.possystem.backend.common.ratelimit.DistributedRateLimiterService;
import com.possystem.backend.common.ratelimit.RateLimitResult;
import com.possystem.backend.order.dto.OrderDetailRequest;
import com.possystem.backend.order.dto.OrderRequest;
import com.possystem.backend.order.dto.OrderResponse;
import com.possystem.backend.order.service.OrderService;
import com.possystem.backend.payment.dto.PaymentRequest;
import com.possystem.backend.payment.dto.PaymentResponse;
import com.possystem.backend.payment.service.PaymentService;
import com.possystem.backend.product.dto.ProductFilterRequest;
import com.possystem.backend.product.dto.ProductResponse;
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.product.service.ProductService;
import com.possystem.backend.report.dto.DashboardReportResponse;
import com.possystem.backend.report.service.ReportService;
import com.possystem.backend.shift.entity.Shift;
import com.possystem.backend.shift.repository.ShiftRepository;
import com.possystem.backend.supplier.entity.Supplier;
import com.possystem.backend.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies complete POS operational continuity when Redis is completely offline / unreachable.
 * Core POS catalog, dashboard, order creation, payment, stock deduction, and rate limiting
 * must degrade gracefully to MySQL/H2 DB and local token buckets with zero business errors.
 */
@SpringBootTest(properties = {
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=16399",
        "spring.data.redis.connect-timeout=150ms",
        "spring.data.redis.timeout=150ms",
        "app.cache.circuit-breaker-cooldown-ms=10000"
})
@ActiveProfiles("test")
class RedisOutageFallbackIntegrationTest {

    @Autowired private ProductService productService;
    @Autowired private CategoryService categoryService;
    @Autowired private ReportService reportService;
    @Autowired private OrderService orderService;
    @Autowired private PaymentService paymentService;
    @Autowired private DistributedRateLimiterService rateLimiterService;
    @Autowired private RedisCircuitBreaker circuitBreaker;
    @Autowired private CacheMetricsService cacheMetricsService;

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ShiftRepository shiftRepository;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        circuitBreaker.reset();
        cacheMetricsService.reset();
        rateLimiterService.resetLocalBuckets();

        if (shiftRepository.findByStatus(ShiftStatus.OPEN).isEmpty()) {
            shiftRepository.save(Shift.builder()
                    .shiftCode("SHIFT-OUTAGE-001")
                    .startTime(LocalDateTime.now())
                    .openingCash(BigDecimal.valueOf(500000))
                    .status(ShiftStatus.OPEN)
                    .terminalId("MAIN_COUNTER")
                    .build());
        }

        Category category = categoryRepository.findByName("OUTAGE_CAT")
                .orElseGet(() -> categoryRepository.save(Category.builder().name("OUTAGE_CAT").build()));

        Supplier supplier = supplierRepository.findByName("OUTAGE_SUP")
                .orElseGet(() -> supplierRepository.save(Supplier.builder().name("OUTAGE_SUP").phone("0900112233").build()));

        testProduct = productRepository.save(Product.builder()
                .productCode("PROD-OUT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .name("Espresso Fallback")
                .price(BigDecimal.valueOf(30000))
                .cost(BigDecimal.valueOf(10000))
                .unit("Ly")
                .stock(10)
                .status(ProductStatus.ACTIVE)
                .category(category)
                .supplier(supplier)
                .build());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("Redis Outage: Business APIs, POS Checkout & Rate Limiting Operate Seamlessly Without Redis")
    void testFullPosWorkflow_WhenRedisIsDown() {
        ProductFilterRequest filter = ProductFilterRequest.builder()
                .page(1)
                .size(20)
                .productCode(testProduct.getProductCode())
                .build();

        // First calls detect dead Redis, trip circuit breaker OPEN, and still return valid DB data
        Page<ProductResponse> p1 = productService.filterProducts(filter);
        Page<ProductResponse> p2 = productService.filterProducts(filter);
        assertEquals(1, p1.getTotalElements());
        assertEquals(1, p2.getTotalElements());
        assertEquals("OPEN", circuitBreaker.getState(), "Circuit breaker must trip OPEN after Redis failures");

        // Subsequent calls while circuit is OPEN bypass Redis instantly (< 50ms) and query DB directly
        long startNs = System.nanoTime();
        var cats = categoryService.getAll(0, 10, null);
        DashboardReportResponse dash = reportService.getDashboardReport();
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        assertTrue(cats.getTotalElements() >= 1);
        assertNotNull(dash);
        assertTrue(elapsedMs < 2000, "Circuit-open fallback should execute swiftly via DB");

        // Checkout & Payment continue reliably on DB
        OrderResponse order = orderService.createOrder(OrderRequest.builder()
                .orderDetails(List.of(OrderDetailRequest.builder()
                        .productId(testProduct.getId())
                        .quantity(2)
                        .build()))
                .build());

        PaymentResponse payment = paymentService.createPayment(PaymentRequest.builder()
                .orderId(order.getId())
                .method(PaymentMethod.CASH)
                .cashReceived(BigDecimal.valueOf(100000))
                .idempotencyKey("OUTAGE-PAY-" + UUID.randomUUID())
                .build());

        assertEquals(0, BigDecimal.valueOf(60000).compareTo(payment.getAmount()));
        assertEquals(8, productService.getProductCode(testProduct.getProductCode()).getStock().intValue());

        // Rate limiter seamlessly falls back to LOCAL_FALLBACK token bucket
        String ip = "198.51.100.10";
        RateLimitResult rl1 = rateLimiterService.checkRequest("VOUCHER_DISCOUNT", "voucher", "cashierOutage", ip, 2);
        RateLimitResult rl2 = rateLimiterService.checkRequest("VOUCHER_DISCOUNT", "voucher", "cashierOutage", ip, 2);
        RateLimitResult rl3 = rateLimiterService.checkRequest("VOUCHER_DISCOUNT", "voucher", "cashierOutage", ip, 2);

        assertEquals("LOCAL_FALLBACK", rl1.getMode());
        assertTrue(rl1.isAllowed());
        assertTrue(rl2.isAllowed());
        assertFalse(rl3.isAllowed(), "Local fallback rate limiter must still enforce quota when Redis is down");
    }
}
