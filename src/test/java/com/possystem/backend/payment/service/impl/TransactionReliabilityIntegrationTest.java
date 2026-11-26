package com.possystem.backend.payment.service.impl;

import com.possystem.backend.category.entity.Category;
import com.possystem.backend.category.repository.CategoryRepository;
import com.possystem.backend.common.enums.OrderStatus;
import com.possystem.backend.common.enums.PaymentMethod;
import com.possystem.backend.common.enums.PaymentStatus;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.common.enums.ShiftStatus;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.discount.dto.DiscountRequest;
import com.possystem.backend.discount.entity.Discount;
import com.possystem.backend.discount.entity.DiscountUsage;
import com.possystem.backend.discount.repository.DiscountRepository;
import com.possystem.backend.discount.repository.DiscountUsageRepository;
import com.possystem.backend.discount.service.DiscountService;
import com.possystem.backend.order.dto.OrderDetailRequest;
import com.possystem.backend.order.dto.OrderRequest;
import com.possystem.backend.order.dto.OrderResponse;
import com.possystem.backend.order.entity.OrderDetail;
import com.possystem.backend.order.entity.Orders;
import com.possystem.backend.order.repository.OrderRepository;
import com.possystem.backend.order.service.OrderService;
import com.possystem.backend.payment.dto.PaymentRequest;
import com.possystem.backend.payment.dto.PaymentResponse;
import com.possystem.backend.payment.entity.Payment;
import com.possystem.backend.payment.repository.PaymentRepository;
import com.possystem.backend.payment.service.PaymentService;
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.product.entity.StockMovement;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.product.repository.StockMovementRepository;
import com.possystem.backend.role.entity.Role;
import com.possystem.backend.role.repository.RoleRepository;
import com.possystem.backend.shift.entity.Shift;
import com.possystem.backend.shift.repository.ShiftRepository;
import com.possystem.backend.supplier.entity.Supplier;
import com.possystem.backend.supplier.repository.SupplierRepository;
import com.possystem.backend.user.entity.CustomerProfile;
import com.possystem.backend.user.entity.LoyaltyTransaction;
import com.possystem.backend.user.entity.User;
import com.possystem.backend.user.repository.CustomerProfileRepository;
import com.possystem.backend.user.repository.LoyaltyTransactionRepository;
import com.possystem.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class TransactionReliabilityIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private DiscountService discountService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private LoyaltyTransactionRepository loyaltyTransactionRepository;

    @Autowired
    private DiscountRepository discountRepository;

    @Autowired
    private DiscountUsageRepository discountUsageRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Product testProduct;
    private User testCustomer;
    private CustomerProfile testProfile;
    private Shift testShift;

    @BeforeEach
    void setUp() {
        // Tạo ca làm việc mở (Shift Guard)
        shiftRepository.deleteAll();
        testShift = shiftRepository.save(Shift.builder()
                .shiftCode("SHIFT-TEST-001")
                .startTime(LocalDateTime.now())
                .openingCash(BigDecimal.valueOf(1000000))
                .status(ShiftStatus.OPEN)
                .terminalId("MAIN_COUNTER")
                .build());

        // Tạo danh mục & nhà cung cấp
        Category category = categoryRepository.findByName("COFFEE")
                .orElseGet(() -> categoryRepository.save(Category.builder().name("COFFEE").description("Coffee").build()));

        Supplier supplier = supplierRepository.findByName("SUPPLIER_TEST")
                .orElseGet(() -> supplierRepository.save(Supplier.builder().name("SUPPLIER_TEST").phone("0988888888").build()));

        // Tạo sản phẩm thử nghiệm với stock = 10
        String pCode = "PROD-TEST-" + System.currentTimeMillis();
        testProduct = productRepository.save(Product.builder()
                .productCode(pCode)
                .name("Cà phê Đẳng Cấp")
                .price(BigDecimal.valueOf(35000))
                .cost(BigDecimal.valueOf(15000))
                .stock(10)
                .status(ProductStatus.ACTIVE)
                .category(category)
                .supplier(supplier)
                .build());

        // Tạo khách hàng thân thiết
        String phone = "09" + ThreadLocalRandom.current().nextInt(10000000, 99999999);
        testProfile = CustomerProfile.builder()
                .loyaltyPoints(5000)
                .membershipLevel("GOLD")
                .build();

        testCustomer = User.builder()
                .fullName("Khách hàng VIP")
                .phone(phone)
                .customerProfile(testProfile)
                .build();
        testProfile.setUser(testCustomer);

        testCustomer = userRepository.save(testCustomer);
        testProfile = testCustomer.getCustomerProfile();
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("Reliability 1: Idempotency Key - Thanh toán lặp lại trả về cùng kết quả, không trừ đúp")
    void testDuplicatePaymentProtection_IdempotencyKey() {
        // 1. Tạo đơn hàng 1 món
        OrderResponse orderRes = orderService.createOrder(OrderRequest.builder()
                .userId(testCustomer.getId())
                .orderDetails(List.of(OrderDetailRequest.builder()
                        .productId(testProduct.getId())
                        .quantity(2)
                        .build()))
                .build());

        String idempotencyKey = "IDEMPOTENT-KEY-" + UUID.randomUUID();

        PaymentRequest request = PaymentRequest.builder()
                .orderId(orderRes.getId())
                .method(PaymentMethod.CASH)
                .cashReceived(BigDecimal.valueOf(100000))
                .userPhone(testCustomer.getPhone())
                .idempotencyKey(idempotencyKey)
                .note("Test idempotency key")
                .build();

        // 2. Gửi request thanh toán lần 1
        PaymentResponse response1 = paymentService.createPayment(request);
        assertNotNull(response1);
        assertEquals(PaymentStatus.SUCCESS, response1.getStatus());

        // Tồn kho ban đầu là 10, mua 2 -> còn 8
        Product updatedProduct1 = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(8, updatedProduct1.getStock());

        // 3. Gửi request thanh toán lần 2 với cùng idempotencyKey (mô phỏng thu ngân bấm đúp hoặc network retry)
        PaymentResponse response2 = paymentService.createPayment(request);
        assertNotNull(response2);
        assertEquals(response1.getId(), response2.getId(), "Phải trả về cùng ID giao dịch đã thanh toán");
        assertEquals(response1.getTransactionId(), response2.getTransactionId());

        // Tồn kho vẫn phải là 8, không được trừ lần 2!
        Product updatedProduct2 = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(8, updatedProduct2.getStock(), "Tồn kho không được trừ trùng khi gửi request lặp lại");

        // Bảng payment chỉ có 1 bản ghi duy nhất cho order này
        assertEquals(1, paymentRepository.findAll().stream().filter(p -> p.getOrder().getId().equals(orderRes.getId())).count());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("Reliability 2: Concurrent Stock Deduction - Không bao giờ bán âm kho dưới tải đồng thời")
    void testConcurrentStockDeduction_PessimisticLocking() throws InterruptedException {
        int initialStock = testProduct.getStock(); // 10
        int concurrentThreads = 15; // 15 luồng cùng tranh mua 1 món (chỉ 10 thành công, 5 phải fail)

        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        CountDownLatch readyLatch = new CountDownLatch(concurrentThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger outOfStockCount = new AtomicInteger(0);

        SecurityContext securityContext = SecurityContextHolder.getContext();

        for (int i = 0; i < concurrentThreads; i++) {
            executor.submit(() -> {
                SecurityContextHolder.setContext(securityContext);
                readyLatch.countDown();
                try {
                    startLatch.await(); // Chờ tất cả luồng sẵn sàng rồi bắn đồng loạt

                    // Tạo đơn 1 sản phẩm
                    OrderResponse oRes = orderService.createOrder(OrderRequest.builder()
                            .orderDetails(List.of(OrderDetailRequest.builder()
                                    .productId(testProduct.getId())
                                    .quantity(1)
                                    .build()))
                            .build());

                    paymentService.createPayment(PaymentRequest.builder()
                            .orderId(oRes.getId())
                            .method(PaymentMethod.CASH)
                            .cashReceived(BigDecimal.valueOf(50000))
                            .build());

                    successCount.incrementAndGet();
                } catch (AppException e) {
                    if (e.getErrorCode() == ErrorCode.PRODUCT_OUT_OF_STOCK) {
                        outOfStockCount.incrementAndGet();
                    } else {
                        System.err.println("AppException: " + e.getErrorCode());
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown(); // Bắn đồng thời
        doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        Product finalProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(0, finalProduct.getStock(), "Tồn kho phải về đúng 0 sau khi hết 10 món");
        assertEquals(ProductStatus.OUT_OF_STOCK, finalProduct.getStatus(), "Trạng thái phải tự động chuyển sang OUT_OF_STOCK");
        assertEquals(initialStock, successCount.get(), "Chỉ có đúng 10 đơn hàng được phép thanh toán thành công");
        assertEquals(5, outOfStockCount.get(), "5 đơn vượt quá tồn kho phải bị từ chối với mã PRODUCT_OUT_OF_STOCK");
        assertTrue(finalProduct.getStock() >= 0, "Tuyệt đối không bao giờ được âm kho!");
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("Reliability 3: Transaction Rollback - Lỗi giữa chừng thì hoàn trả toàn bộ tồn kho và điểm")
    void testTransactionRollback_Atomicity() {
        int initialStock = testProduct.getStock(); // 10

        OrderResponse orderRes = orderService.createOrder(OrderRequest.builder()
                .userId(testCustomer.getId())
                .orderDetails(List.of(OrderDetailRequest.builder()
                        .productId(testProduct.getId())
                        .quantity(3)
                        .build()))
                .build());

        // Cố tình đưa thiếu tiền mặt để kích hoạt rollback trong createPayment
        PaymentRequest invalidPaymentReq = PaymentRequest.builder()
                .orderId(orderRes.getId())
                .method(PaymentMethod.CASH)
                .cashReceived(BigDecimal.valueOf(10000)) // Thiếu tiền (Đơn 3 * 35k = 105k)
                .userPhone(testCustomer.getPhone())
                .build();

        assertThrows(AppException.class, () -> paymentService.createPayment(invalidPaymentReq));

        // Kiểm tra tồn kho sản phẩm sau khi rollback: Vẫn nguyên vẹn 10
        Product rolledBackProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(initialStock, rolledBackProduct.getStock(), "Tồn kho phải được khôi phục nguyên vẹn khi giao dịch thanh toán rollback");

        // Đơn hàng chưa chuyển sang PAID
        Orders orderInDb = orderRepository.findById(orderRes.getId()).orElseThrow();
        assertNotEquals(OrderStatus.PAID, orderInDb.getStatus(), "Trạng thái đơn hàng không được là PAID khi thanh toán thất bại");
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("Reliability 4: Voucher Usage Limit - Giới hạn số lượt dùng và chống dùng vượt định mức")
    void testVoucherUsageLimit_Enforcement() {
        String code = "VOUCHER-LIMIT-1-" + System.currentTimeMillis();
        Discount discount = discountRepository.save(Discount.builder()
                .name("Giảm 10k Giới Hạn 1 Lượt")
                .code(code)
                .value(BigDecimal.valueOf(10000))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(5))
                .active(true)
                .usageLimit(1) // Chỉ cho phép đúng 1 lượt dùng
                .usedCount(0)
                .build());

        // Lần 1: Dùng mã thành công
        Orders order1 = Orders.builder()
                .orderCode("ORD-V1-" + System.currentTimeMillis())
                .totalPrice(BigDecimal.valueOf(50000))
                .status(OrderStatus.PENDING)
                .build();
        order1 = orderRepository.save(order1);

        BigDecimal discountApplied = discountService.applyDiscountCode(order1, code);
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(discountApplied));

        Discount updatedDiscount = discountRepository.findById(discount.getId()).orElseThrow();
        assertEquals(1, updatedDiscount.getUsedCount());

        // Lần 2: Đơn khác dùng lại mã này -> Phải ném ngoại lệ vì đã đạt usageLimit
        Orders order2 = Orders.builder()
                .orderCode("ORD-V2-" + System.currentTimeMillis())
                .totalPrice(BigDecimal.valueOf(50000))
                .status(OrderStatus.PENDING)
                .build();
        order2 = orderRepository.save(order2);

        Orders finalOrder2 = order2;
        AppException ex = assertThrows(AppException.class, () -> discountService.applyDiscountCode(finalOrder2, code));
        assertEquals(ErrorCode.INVALID_DISCOUNT_CODE, ex.getErrorCode());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("Reliability 5: Loyalty Idempotency - Không thể chèn lặp giao dịch điểm có cùng Idempotency Key")
    void testLoyaltyTransaction_IdempotencyKeyConstraint() {
        String idempotencyKey = "LOYALTY-KEY-" + UUID.randomUUID();

        LoyaltyTransaction tx1 = LoyaltyTransaction.builder()
                .customerProfile(testProfile)
                .transactionType("EARN")
                .pointsChange(100)
                .balanceAfter(5100)
                .idempotencyKey(idempotencyKey)
                .note("Tích điểm lần 1")
                .build();
        loyaltyTransactionRepository.save(tx1);

        assertTrue(loyaltyTransactionRepository.existsByIdempotencyKey(idempotencyKey));

        // Cố tình lưu lần 2 với cùng idempotencyKey -> Bị chặn bởi unique constraint
        LoyaltyTransaction tx2 = LoyaltyTransaction.builder()
                .customerProfile(testProfile)
                .transactionType("EARN")
                .pointsChange(100)
                .balanceAfter(5200)
                .idempotencyKey(idempotencyKey)
                .note("Cố tình cộng điểm lặp")
                .build();

        assertThrows(Exception.class, () -> loyaltyTransactionRepository.saveAndFlush(tx2),
                "Database phải từ chối giao dịch điểm có trùng idempotency_key");
    }
}
