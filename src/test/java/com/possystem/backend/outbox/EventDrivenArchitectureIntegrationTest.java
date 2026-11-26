package com.possystem.backend.outbox;

import com.possystem.backend.category.entity.Category;
import com.possystem.backend.category.repository.CategoryRepository;
import com.possystem.backend.common.enums.*;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.outbox.consumer.IdempotentConsumerExecutor;
import com.possystem.backend.common.outbox.consumer.PosEventConsumer;
import com.possystem.backend.common.outbox.entity.*;
import com.possystem.backend.common.outbox.event.DomainEventEnvelope;
import com.possystem.backend.common.outbox.event.PosEventType;
import com.possystem.backend.common.outbox.repository.*;
import com.possystem.backend.common.outbox.service.OutboxProcessor;
import com.possystem.backend.common.outbox.service.OutboxPublisher;
import com.possystem.backend.common.outbox.transport.EventTransportException;
import com.possystem.backend.common.outbox.transport.OutboxEventTransport;
import com.possystem.backend.discount.entity.Discount;
import com.possystem.backend.discount.repository.DiscountRepository;
import com.possystem.backend.discount.repository.DiscountUsageRepository;
import com.possystem.backend.order.dto.OrderDetailRequest;
import com.possystem.backend.order.dto.OrderRequest;
import com.possystem.backend.order.dto.OrderResponse;
import com.possystem.backend.order.entity.Orders;
import com.possystem.backend.order.repository.OrderRepository;
import com.possystem.backend.order.service.OrderService;
import com.possystem.backend.payment.dto.PaymentRequest;
import com.possystem.backend.payment.dto.PaymentResponse;
import com.possystem.backend.payment.dto.RefundItemRequest;
import com.possystem.backend.payment.dto.RefundRequest;
import com.possystem.backend.payment.repository.PaymentRepository;
import com.possystem.backend.payment.service.PaymentService;
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.product.repository.StockMovementRepository;
import com.possystem.backend.shift.dto.CloseShiftRequest;
import com.possystem.backend.shift.entity.Shift;
import com.possystem.backend.shift.repository.ShiftRepository;
import com.possystem.backend.shift.service.ShiftService;
import com.possystem.backend.supplier.entity.Supplier;
import com.possystem.backend.supplier.repository.SupplierRepository;
import com.possystem.backend.user.entity.CustomerProfile;
import com.possystem.backend.user.entity.User;
import com.possystem.backend.user.repository.CustomerProfileRepository;
import com.possystem.backend.user.repository.LoyaltyTransactionRepository;
import com.possystem.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventDrivenArchitectureIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private OrderService orderService;
    @Autowired private PaymentService paymentService;
    @Autowired private ShiftService shiftService;
    @Autowired private OutboxPublisher outboxPublisher;
    @Autowired private OutboxProcessor outboxProcessor;
    @Autowired private OutboxEventTransport localTransport;
    @Autowired private IdempotentConsumerExecutor idempotentConsumerExecutor;

    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private ProcessedEventRepository processedEventRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private InvoiceRecordRepository invoiceRecordRepository;
    @Autowired private DailySalesProjectionRepository projectionRepository;

    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ShiftRepository shiftRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CustomerProfileRepository customerProfileRepository;
    @Autowired private LoyaltyTransactionRepository loyaltyTransactionRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private DiscountRepository discountRepository;
    @Autowired private DiscountUsageRepository discountUsageRepository;

    private Product testProduct;
    private User testCustomer;
    private User testCashier;
    private CustomerProfile testProfile;
    private Shift openShift;

    @BeforeEach
    void cleanSlate() {
        outboxEventRepository.deleteAll();
        processedEventRepository.deleteAll();
        auditEventRepository.deleteAll();
        invoiceRecordRepository.deleteAll();
        projectionRepository.deleteAll();
        loyaltyTransactionRepository.deleteAll();
        stockMovementRepository.deleteAll();
        discountUsageRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        discountRepository.deleteAll();
        shiftRepository.deleteAll();

        testCashier = userRepository.findByUsername("cashier_evt_test")
                .orElseGet(() -> userRepository.save(User.builder()
                        .username("cashier_evt_test")
                        .password("secret")
                        .fullName("Thu Ngan Event")
                        .status(UserStatus.ACTIVE)
                        .build()));

        openShift = shiftRepository.save(Shift.builder()
                .shiftCode("SHIFT-EVT-" + System.currentTimeMillis())
                .startTime(LocalDateTime.now().minusHours(1))
                .openingCash(BigDecimal.valueOf(1000000))
                .status(ShiftStatus.OPEN)
                .terminalId("MAIN_COUNTER")
                .openedBy(testCashier)
                .build());

        Category cat = categoryRepository.findByName("EVENT_COFFEE")
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name("EVENT_COFFEE")
                        .description("Event Coffee")
                        .build()));

        Supplier sup = supplierRepository.findByName("EVENT_SUPPLIER")
                .orElseGet(() -> supplierRepository.save(Supplier.builder()
                        .name("EVENT_SUPPLIER")
                        .phone("0911111111")
                        .build()));

        testProduct = productRepository.save(Product.builder()
                .productCode("EVT-PRD-" + UUID.randomUUID().toString().substring(0, 6))
                .name("Espresso Đẳng Cấp")
                .price(BigDecimal.valueOf(50000))
                .cost(BigDecimal.valueOf(20000))
                .stock(20)
                .status(ProductStatus.ACTIVE)
                .category(cat)
                .supplier(sup)
                .build());

        testCustomer = userRepository.findByPhone("0977777777")
                .orElseGet(() -> {
                    User u = userRepository.save(User.builder()
                            .username("cust_evt_" + UUID.randomUUID().toString().substring(0, 6))
                            .phone("0977777777")
                            .fullName("Khach Hang Loyalty")
                            .password("secret")
                            .status(UserStatus.ACTIVE)
                            .build());
                    CustomerProfile profile = CustomerProfile.builder()
                            .user(u)
                            .loyaltyPoints(95)
                            .membershipLevel("MEMBER")
                            .build();
                    customerProfileRepository.save(profile);
                    u.setCustomerProfile(profile);
                    return u;
                });

        testProfile = customerProfileRepository.findById(testCustomer.getCustomerProfile().getId()).orElseThrow();
        testProfile.setLoyaltyPoints(95);
        testProfile.setMembershipLevel("MEMBER");
        customerProfileRepository.save(testProfile);
    }

    @Test
    @WithMockUser(username = "cashier_evt_test", roles = {"ADMIN", "MANAGE", "EMPLOYEE"})
    @DisplayName("1. Full Business Event Flow -> Outbox Worker -> Async Invoice, Loyalty Tier, Audit & Analytics Projection")
    void endToEndBusinessEvents_AndAsyncConsumers_Succeed() {
        discountRepository.save(Discount.builder()
                .name("Voucher Event Test")
                .code("EVT20K")
                .value(BigDecimal.valueOf(20000))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .active(true)
                .usageLimit(10)
                .usedCount(0)
                .build());

        // 1. Create Order with Voucher -> OrderCreated + VoucherRedeemed
        OrderResponse order = orderService.createOrder(OrderRequest.builder()
                .discountCode("EVT20K")
                .orderDetails(List.of(OrderDetailRequest.builder()
                        .productId(testProduct.getId())
                        .quantity(12) // 12 * 50k = 600k - 20k = 580k -> 5800 points (>= 500 -> GOLD!)
                        .build()))
                .build());

        // 2. Pay Order -> PaymentCompleted + OrderCompleted + InventoryUpdated + LoyaltyPointsEarned
        PaymentResponse payment = paymentService.createPayment(PaymentRequest.builder()
                .orderId(order.getId())
                .method(PaymentMethod.CASH)
                .cashReceived(BigDecimal.valueOf(600000))
                .userPhone("0977777777")
                .build());

        // 3. Partial Refund -> InventoryUpdated (REFUND)
        RefundItemRequest refundItem = new RefundItemRequest();
        refundItem.setProductId(testProduct.getId());
        refundItem.setQuantity(1);
        paymentService.refundItems(payment.getId(), RefundRequest.builder()
                .reason("Customer changed mind")
                .items(List.of(refundItem))
                .build());

        // 4. Create second order and cancel it -> OrderCreated + OrderCancelled
        OrderResponse cancelledOrder = orderService.createOrder(OrderRequest.builder()
                .orderDetails(List.of(OrderDetailRequest.builder()
                        .productId(testProduct.getId())
                        .quantity(1)
                        .build()))
                .build());
        orderService.cancelOrder(cancelledOrder.getId(), "CUSTOMER_ABORTED");

        // 5. Close shift -> ShiftClosed
        shiftService.closeShift(CloseShiftRequest.builder().note500k(4L).build());

        // Verify all domain events sit in PENDING before worker poll
        long pendingCount = outboxEventRepository.countByStatus(OutboxEvent.OutboxStatus.PENDING);
        assertTrue(pendingCount >= 9, "Expected at least 9 pending business events, got " + pendingCount);

        // Execute Outbox Worker (multiple cycles if needed for strict ordering of same aggregate)
        outboxProcessor.processOutboxEvents();
        outboxProcessor.processOutboxEvents();

        assertEquals(0, outboxEventRepository.countByStatus(OutboxEvent.OutboxStatus.PENDING));
        assertEquals(0, outboxEventRepository.countByStatus(OutboxEvent.OutboxStatus.DEAD_LETTER));

        // Verify InvoiceRecord asynchronously generated
        InvoiceRecord invoice = invoiceRecordRepository.findByPaymentId(payment.getId()).orElse(null);
        assertNotNull(invoice, "InvoiceRecord should be generated asynchronously");
        assertEquals(order.getId(), invoice.getOrderId());
        assertEquals(order.getOrderCode(), invoice.getOrderCode());
        assertTrue(invoice.getRenderedHtml().contains("DANG CAP STORE"));
        assertTrue(invoice.getRenderedHtml().contains(payment.getTransactionId()));

        // Verify Loyalty Tier asynchronously upgraded (95 + 5800 -> GOLD >= 500, actually PLATINUM >= 2000!)
        CustomerProfile updatedProfile = customerProfileRepository.findById(testProfile.getId()).orElseThrow();
        assertEquals("PLATINUM", updatedProfile.getMembershipLevel());

        // Verify Audit Trail has all expected event types
        List<AuditEvent> audits = auditEventRepository.findAll();
        assertTrue(audits.stream().anyMatch(a -> PosEventType.ORDER_CREATED.equals(a.getEventType())));
        assertTrue(audits.stream().anyMatch(a -> PosEventType.VOUCHER_REDEEMED.equals(a.getEventType())));
        assertTrue(audits.stream().anyMatch(a -> PosEventType.PAYMENT_COMPLETED.equals(a.getEventType())));
        assertTrue(audits.stream().anyMatch(a -> PosEventType.ORDER_COMPLETED.equals(a.getEventType())));
        assertTrue(audits.stream().anyMatch(a -> PosEventType.INVENTORY_UPDATED.equals(a.getEventType())));
        assertTrue(audits.stream().anyMatch(a -> PosEventType.LOYALTY_POINTS_EARNED.equals(a.getEventType())));
        assertTrue(audits.stream().anyMatch(a -> PosEventType.ORDER_CANCELLED.equals(a.getEventType())));
        assertTrue(audits.stream().anyMatch(a -> PosEventType.SHIFT_CLOSED.equals(a.getEventType())));

        // Verify Analytics Projection
        DailySalesProjection projection = projectionRepository.findByMetricDate(LocalDate.now()).orElse(null);
        assertNotNull(projection);
        assertEquals(2L, projection.getOrdersCreated());
        assertEquals(1L, projection.getOrdersCompleted());
        assertEquals(1L, projection.getOrdersCancelled());
        assertEquals(1L, projection.getPaymentsCompleted());
        assertEquals(1L, projection.getVouchersRedeemed());
        assertEquals(1L, projection.getShiftsClosed());
        assertEquals(0, BigDecimal.valueOf(580000).compareTo(projection.getTotalRevenue()));
    }

    @Test
    @WithMockUser(username = "cashier_evt_test", roles = {"ADMIN", "MANAGE", "EMPLOYEE"})
    @DisplayName("2. DB Transaction Rollback Simulation: No phantom PaymentCompleted/InventoryUpdated emitted, PaymentFailed preserved")
    void dbRollback_NoGhostSuccessEvents_FailureEventRecorded() {
        OrderResponse order = orderService.createOrder(OrderRequest.builder()
                .orderDetails(List.of(OrderDetailRequest.builder()
                        .productId(testProduct.getId())
                        .quantity(25) // exceeds stock 20 -> triggers PRODUCT_OUT_OF_STOCK rollback
                        .build()))
                .build());

        outboxProcessor.processOutboxEvents(); // flush OrderCreated first
        long initialEvents = outboxEventRepository.count();

        AppException ex = assertThrows(AppException.class, () -> paymentService.createPayment(PaymentRequest.builder()
                .orderId(order.getId())
                .method(PaymentMethod.CASH)
                .cashReceived(BigDecimal.valueOf(2000000))
                .build()));

        assertEquals(ErrorCode.PRODUCT_OUT_OF_STOCK, ex.getErrorCode());

        // Order stays PENDING & stock stays untouched
        Orders unchangedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.PENDING, unchangedOrder.getStatus());
        assertEquals(20, productRepository.findById(testProduct.getId()).orElseThrow().getStock());

        // Zero phantom success events
        List<OutboxEvent> allEvents = outboxEventRepository.findAll();
        assertTrue(allEvents.stream().noneMatch(e -> PosEventType.PAYMENT_COMPLETED.equals(e.getEventType())));
        assertTrue(allEvents.stream().noneMatch(e -> PosEventType.ORDER_COMPLETED.equals(e.getEventType())));
        assertTrue(allEvents.stream().noneMatch(e -> PosEventType.INVENTORY_UPDATED.equals(e.getEventType())));

        // PaymentFailed was cleanly recorded via isolated REQUIRES_NEW audit TX
        assertEquals(initialEvents + 1, allEvents.size());
        assertTrue(allEvents.stream().anyMatch(e -> PosEventType.PAYMENT_FAILED.equals(e.getEventType())));

        outboxProcessor.processOutboxEvents();
        DailySalesProjection projection = projectionRepository.findByMetricDate(LocalDate.now()).orElseThrow();
        assertEquals(1L, projection.getPaymentsFailed());
        assertEquals(0L, projection.getPaymentsCompleted());
    }

    @Test
    @DisplayName("3. Worker Crash Simulation: Stale PROCESSING lock reclaimed and event processed cleanly")
    void workerCrash_StaleLeaseRecoveredAndCompleted() {
        OutboxEvent crashedEvent = OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId("ord-crash-1")
                .eventType(PosEventType.ORDER_CREATED)
                .orderingKey("order:ord-crash-1")
                .correlationId("ORD-CRASH-1")
                .payload("{\"orderId\":\"ord-crash-1\",\"orderCode\":\"ORD-CRASH-1\"}")
                .status(OutboxEvent.OutboxStatus.PROCESSING)
                .lockedAt(LocalDateTime.now().minusSeconds(30)) // older than 5s test lease
                .lockedBy("crashed-worker-node-9")
                .retryCount(0)
                .maxRetries(5)
                .createdAt(LocalDateTime.now().minusSeconds(35))
                .build();
        crashedEvent = outboxEventRepository.save(crashedEvent);

        int recovered = outboxProcessor.recoverStaleLocks();
        assertEquals(1, recovered);

        OutboxEvent afterRecover = outboxEventRepository.findById(crashedEvent.getId()).orElseThrow();
        assertEquals(OutboxEvent.OutboxStatus.FAILED, afterRecover.getStatus());
        assertEquals(1, afterRecover.getRetryCount());
        assertNull(afterRecover.getLockedAt());

        int processed = outboxProcessor.processOutboxEvents();
        assertEquals(1, processed);

        OutboxEvent completed = outboxEventRepository.findById(crashedEvent.getId()).orElseThrow();
        assertEquals(OutboxEvent.OutboxStatus.PROCESSED, completed.getStatus());
        assertTrue(auditEventRepository.existsByEventId(completed.getId()));
    }

    @Test
    @DisplayName("4. Duplicate Delivery, Event Replay & Partial Consumer Failure Idempotency")
    void duplicateAndPartialFailure_ProtectedByIdempotentConsumer() {
        String payId = "pay-idem-" + UUID.randomUUID();
        OutboxEvent paymentEvent = outboxPublisher.publish(
                "PAYMENT",
                payId,
                PosEventType.PAYMENT_COMPLETED,
                "order:ord-idem",
                "ORD-IDEM-1",
                Map.of(
                        "paymentId", payId,
                        "orderId", "ord-idem",
                        "orderCode", "ORD-IDEM-1",
                        "amount", 125000,
                        "method", "CASH"
                ));

        // 1. First delivery
        assertEquals(1, outboxProcessor.processOutboxEvents());
        DailySalesProjection proj1 = projectionRepository.findByMetricDate(LocalDate.now()).orElseThrow();
        assertEquals(1L, proj1.getPaymentsCompleted());
        assertEquals(0, BigDecimal.valueOf(125000).compareTo(proj1.getTotalRevenue()));
        assertEquals(1L, invoiceRecordRepository.count());

        // 2. Manual Event Replay on PROCESSED event (simulating duplicate delivery / operator replay)
        outboxProcessor.replayEvent(paymentEvent.getId());
        assertEquals(1, outboxProcessor.processOutboxEvents());

        // Projection & Invoice & Audit counts MUST NOT double-count!
        DailySalesProjection proj2 = projectionRepository.findByMetricDate(LocalDate.now()).orElseThrow();
        assertEquals(1L, proj2.getPaymentsCompleted());
        assertEquals(0, BigDecimal.valueOf(125000).compareTo(proj2.getTotalRevenue()));
        assertEquals(1L, invoiceRecordRepository.count());
        assertEquals(1L, auditEventRepository.count());

        // 3. Partial Consumer Failure Recovery test: Consumer A succeeds, Consumer B fails attempt 1, succeeds attempt 2
        AtomicInteger consumerACalls = new AtomicInteger(0);
        AtomicInteger consumerBCalls = new AtomicInteger(0);
        AtomicInteger consumerBFailTimes = new AtomicInteger(1);

        PosEventConsumer consumerA = new PosEventConsumer() {
            @Override public String consumerName() { return "TestConsumerA"; }
            @Override public boolean supports(String eventType) { return true; }
            @Override public void consume(DomainEventEnvelope env) { consumerACalls.incrementAndGet(); }
        };
        PosEventConsumer consumerB = new PosEventConsumer() {
            @Override public String consumerName() { return "TestConsumerB"; }
            @Override public boolean supports(String eventType) { return true; }
            @Override public void consume(DomainEventEnvelope env) {
                consumerBCalls.incrementAndGet();
                if (consumerBFailTimes.getAndDecrement() > 0) {
                    throw new IllegalStateException("Transient downstream failure");
                }
            }
        };

        OutboxEvent partialEvent = outboxPublisher.publish(
                "ORDER",
                "ord-partial",
                PosEventType.ORDER_CREATED,
                "order:ord-partial",
                "ORD-PARTIAL",
                Map.of("orderId", "ord-partial", "orderCode", "ORD-PARTIAL"));

        OutboxEventTransport flakyPartialTransport = new OutboxEventTransport() {
            @Override public String transportName() { return "flaky-partial"; }
            @Override public void publish(DomainEventEnvelope env) {
                idempotentConsumerExecutor.execute(consumerA, env);
                idempotentConsumerExecutor.execute(consumerB, env);
            }
        };

        assertFalse(outboxProcessor.dispatchSingleEvent(partialEvent.getId(), flakyPartialTransport));
        OutboxEvent failedPartial = outboxEventRepository.findById(partialEvent.getId()).orElseThrow();
        assertEquals(OutboxEvent.OutboxStatus.FAILED, failedPartial.getStatus());

        // Fast-forward retry window and retry
        failedPartial.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        outboxEventRepository.save(failedPartial);

        assertEquals(1, outboxProcessor.processOutboxEvents(flakyPartialTransport));
        assertEquals(1, consumerACalls.get(), "ConsumerA must execute ONCE and skip on retry");
        assertEquals(2, consumerBCalls.get(), "ConsumerB attempted twice and succeeded on retry");
    }

    @Test
    @DisplayName("5. Delayed Event Ordering: Later OrderCompleted waits for earlier OrderCreated with same orderingKey")
    void delayedEvent_StrictPerAggregateOrderingPreserved() {
        LocalDateTime t0 = LocalDateTime.now().minusSeconds(20);

        // Event 1: OrderCreated delayed in FAILED with future retry
        OutboxEvent event1 = outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId("ord-seq-1")
                .eventType(PosEventType.ORDER_CREATED)
                .orderingKey("order:ord-seq-1")
                .correlationId("ORD-SEQ-1")
                .payload("{\"orderId\":\"ord-seq-1\",\"orderCode\":\"ORD-SEQ-1\"}")
                .status(OutboxEvent.OutboxStatus.FAILED)
                .retryCount(1)
                .maxRetries(5)
                .nextRetryAt(LocalDateTime.now().plusSeconds(60))
                .createdAt(t0)
                .build());

        // Event 2: OrderCompleted arrived for same aggregate (later timestamp)
        OutboxEvent event2 = outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId("ord-seq-1")
                .eventType(PosEventType.ORDER_COMPLETED)
                .orderingKey("order:ord-seq-1")
                .correlationId("ORD-SEQ-1")
                .payload("{\"orderId\":\"ord-seq-1\",\"orderCode\":\"ORD-SEQ-1\",\"status\":\"PAID\"}")
                .status(OutboxEvent.OutboxStatus.PENDING)
                .maxRetries(5)
                .createdAt(t0.plusSeconds(1))
                .build());

        // Event 3: Different aggregate -> should NOT be blocked
        OutboxEvent independentEvent = outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId("ord-seq-2")
                .eventType(PosEventType.ORDER_CREATED)
                .orderingKey("order:ord-seq-2")
                .correlationId("ORD-SEQ-2")
                .payload("{\"orderId\":\"ord-seq-2\",\"orderCode\":\"ORD-SEQ-2\"}")
                .status(OutboxEvent.OutboxStatus.PENDING)
                .maxRetries(5)
                .createdAt(t0.plusSeconds(2))
                .build());

        int count1 = outboxProcessor.processOutboxEvents();
        assertEquals(1, count1, "Only independent ord-seq-2 should be processed while ord-seq-1 Event 1 is incomplete");
        assertEquals(OutboxEvent.OutboxStatus.PENDING, outboxEventRepository.findById(event2.getId()).orElseThrow().getStatus());
        assertEquals(OutboxEvent.OutboxStatus.PROCESSED, outboxEventRepository.findById(independentEvent.getId()).orElseThrow().getStatus());

        // Now Event 1 becomes ready
        event1.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        outboxEventRepository.save(event1);

        int count2 = outboxProcessor.processOutboxEvents();
        assertEquals(2, count2, "Both Event 1 and Event 2 should process strictly in order once Event 1 is due");

        AuditEvent audit1 = auditEventRepository.findByEventId(event1.getId()).orElseThrow();
        AuditEvent audit2 = auditEventRepository.findByEventId(event2.getId()).orElseThrow();
        assertFalse(audit2.getRecordedAt().isBefore(audit1.getRecordedAt()),
                "OrderCompleted must be processed after OrderCreated");
    }

    @Test
    @DisplayName("6. Simulated Kafka/Broker Disconnect -> Exponential Backoff -> DLQ -> Recovery & Replay")
    void brokerDisconnect_ExponentialBackoffToDlq_AndSafeReplayRecovery() {
        long b1 = outboxProcessor.computeExponentialBackoffMs(1);
        long b2 = outboxProcessor.computeExponentialBackoffMs(2);
        long b3 = outboxProcessor.computeExponentialBackoffMs(3);
        assertTrue(b1 < b2 && b2 < b3, "Exponential backoff must strictly increase across retry attempts");

        OutboxEvent event = outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId("ord-kafka-1")
                .eventType(PosEventType.ORDER_CREATED)
                .orderingKey("order:ord-kafka-1")
                .correlationId("ORD-KAFKA-1")
                .payload("{\"orderId\":\"ord-kafka-1\",\"orderCode\":\"ORD-KAFKA-1\"}")
                .status(OutboxEvent.OutboxStatus.PENDING)
                .retryCount(0)
                .maxRetries(3)
                .createdAt(LocalDateTime.now())
                .build());

        OutboxEventTransport disconnectedKafkaTransport = new OutboxEventTransport() {
            @Override public String transportName() { return "simulated-kafka-down"; }
            @Override public void publish(DomainEventEnvelope env) {
                throw new EventTransportException("Simulated Kafka broker disconnected: Connection refused");
            }
        };

        // Attempt 1 -> FAILED
        outboxProcessor.processOutboxEvents(disconnectedKafkaTransport);
        OutboxEvent afterFail1 = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEvent.OutboxStatus.FAILED, afterFail1.getStatus());
        assertEquals(1, afterFail1.getRetryCount());
        assertNotNull(afterFail1.getNextRetryAt());

        // Attempt 2 -> FAILED
        afterFail1.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        outboxEventRepository.save(afterFail1);
        outboxProcessor.processOutboxEvents(disconnectedKafkaTransport);
        OutboxEvent afterFail2 = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEvent.OutboxStatus.FAILED, afterFail2.getStatus());
        assertEquals(2, afterFail2.getRetryCount());

        // Attempt 3 (maxRetries = 3) -> DEAD_LETTER
        afterFail2.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        outboxEventRepository.save(afterFail2);
        outboxProcessor.processOutboxEvents(disconnectedKafkaTransport);
        OutboxEvent deadLetter = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEvent.OutboxStatus.DEAD_LETTER, deadLetter.getStatus());
        assertEquals(3, deadLetter.getRetryCount());
        assertNotNull(deadLetter.getDeadLetteredAt());
        assertTrue(deadLetter.getErrorMessage().contains("Kafka broker disconnected"));

        // Broker reconnects -> Operator triggers DLQ Retry -> Real transport processes cleanly
        Map<String, Object> dlqResult = outboxProcessor.retryDeadLetters();
        assertEquals(1, dlqResult.get("retriedCount"));

        int recoveredCount = outboxProcessor.processOutboxEvents(localTransport);
        assertEquals(1, recoveredCount);

        OutboxEvent finalEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEvent.OutboxStatus.PROCESSED, finalEvent.getStatus());
        assertEquals(1, finalEvent.getReplayedCount());
        assertTrue(auditEventRepository.existsByEventId(finalEvent.getId()));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("7. Admin Outbox Monitoring, DLQ Inspection, Single Replay, Audit & Projection Endpoints")
    void outboxAdminEndpoints_WorkEndToEnd() throws Exception {
        OutboxEvent deadEvent = outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId("ord-api-1")
                .eventType(PosEventType.ORDER_CREATED)
                .orderingKey("order:ord-api-1")
                .correlationId("ORD-API-1")
                .payload("{\"orderId\":\"ord-api-1\",\"orderCode\":\"ORD-API-1\"}")
                .status(OutboxEvent.OutboxStatus.DEAD_LETTER)
                .retryCount(5)
                .maxRetries(5)
                .deadLetteredAt(LocalDateTime.now())
                .errorMessage("Simulated error")
                .createdAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/outbox/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.deadLetter").value(1))
                .andExpect(jsonPath("$.result.transport").value("local-modular-monolith"));

        mockMvc.perform(get("/outbox/dlq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1));

        mockMvc.perform(post("/outbox/events/" + deadEvent.getId() + "/replay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("PENDING"))
                .andExpect(jsonPath("$.result.replayedCount").value(1));

        assertEquals(1, outboxProcessor.processOutboxEvents());

        mockMvc.perform(get("/outbox/audit").param("aggregateId", "ord-api-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1));

        mockMvc.perform(get("/outbox/analytics/daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.ordersCreated").value(1));
    }
}
