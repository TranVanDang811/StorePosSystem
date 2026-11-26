package com.possystem.backend.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.possystem.backend.common.cache.CacheInvalidationService;
import com.possystem.backend.common.enums.OrderStatus;
import com.possystem.backend.common.enums.PaymentMethod;
import com.possystem.backend.common.enums.PaymentStatus;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.observability.PosBusinessMetrics;
import com.possystem.backend.common.outbox.event.PosEventType;
import com.possystem.backend.common.outbox.service.OutboxPublisher;
import com.possystem.backend.common.util.CurrencyUtils;
import com.possystem.backend.common.util.mapper.OrderMapper;
import com.possystem.backend.common.util.mapper.PaymentMapper;
import com.possystem.backend.discount.dto.PointDiscountResult;
import com.possystem.backend.discount.service.DiscountService;
import com.possystem.backend.order.dto.OrderResponse;
import com.possystem.backend.order.dto.OrderDetailResponse;
import com.possystem.backend.order.entity.OrderDetail;
import com.possystem.backend.order.entity.Orders;
import com.possystem.backend.order.repository.OrderRepository;
import com.possystem.backend.payment.dto.*;
import com.possystem.backend.payment.entity.Payment;
import com.possystem.backend.payment.repository.PaymentRepository;
import com.possystem.backend.payment.service.PaymentService;
import com.possystem.backend.payment.util.ThermalReceiptRenderer;
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.common.enums.ShiftStatus;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.shift.repository.ShiftRepository;
import com.possystem.backend.user.dto.CustomerHistoryResponse;
import com.possystem.backend.user.entity.CustomerProfile;
import com.possystem.backend.user.entity.User;
import com.possystem.backend.user.repository.CustomerProfileRepository;
import com.possystem.backend.user.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import com.possystem.backend.product.entity.StockMovement;
import com.possystem.backend.product.repository.StockMovementRepository;
import com.possystem.backend.user.entity.LoyaltyTransaction;
import com.possystem.backend.user.repository.LoyaltyTransactionRepository;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentServiceImpl implements PaymentService {
    PaymentRepository paymentRepository;
    OrderRepository orderRepository;
    PaymentMapper paymentMapper;
    OrderMapper orderMapper;
    final UserRepository userRepository;
    CustomerProfileRepository customerProfileRepository;
    final ObjectMapper objectMapper;
    final DiscountService discountService;
    ProductRepository productRepository;
    ShiftRepository shiftRepository;
    OutboxPublisher outboxPublisher;
    StockMovementRepository stockMovementRepository;
    LoyaltyTransactionRepository loyaltyTransactionRepository;
    CacheInvalidationService cacheInvalidationService;
    PosBusinessMetrics businessMetrics;

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {

        // =========================
        // 0. SHIFT GUARD: Bắt buộc phải có ca làm việc đang mở
        // =========================
        shiftRepository.findByStatus(ShiftStatus.OPEN)
                .orElseThrow(() -> new AppException(ErrorCode.NO_OPENED_SHIFT));

        // 0.1 IDEMPOTENCY KEY CHECK: Chống xử lý trùng lặp request
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            var existingIdempotent = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existingIdempotent.isPresent()) {
                Payment p = existingIdempotent.get();
                log.warn("Idempotent payment detected for key {}. Returning existing record {}.",
                        request.getIdempotencyKey(), p.getId());
                PaymentResponse replay = paymentMapper.toPaymentResponse(p);
                replay.setEarnedPoints(p.getEarnedPoints() != null ? p.getEarnedPoints() : 0);
                replay.setUsedPoints(p.getUsedPoints() != null ? p.getUsedPoints() : 0);
                return replay;
            }
        }

        User user = null;
        CustomerProfile profile = null;
        // =========================
        if (request.getUserPhone() != null && !request.getUserPhone().isBlank()) {

            user = userRepository.findByPhone(request.getUserPhone())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            profile = user.getCustomerProfile();
        }

        // =========================

        Orders order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        Payment existingPayment = paymentRepository.findByOrder_Id(order.getId());
        if (existingPayment != null) {
            // Idempotent replay: Nếu đơn hàng đã hoàn tất thanh toán thành công từ trước,
            // trả về PaymentResponse hiện có
            if (order.getStatus() == OrderStatus.PAID && existingPayment.getStatus() == PaymentStatus.SUCCESS) {
                log.warn(
                        "Idempotent replay detected for orderId {}. Returning existing payment response without re-processing.",
                        order.getId());
                PaymentResponse replay = paymentMapper.toPaymentResponse(existingPayment);
                replay.setEarnedPoints(
                        existingPayment.getEarnedPoints() != null ? existingPayment.getEarnedPoints() : 0);
                replay.setUsedPoints(existingPayment.getUsedPoints() != null ? existingPayment.getUsedPoints() : 0);
                return replay;
            }
            throw new AppException(ErrorCode.PAYMENT_ALREADY_EXISTS);
        }

        try {
            BigDecimal orderFinalAmount = order.getFinalAmount();

            if (orderFinalAmount == null || orderFinalAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new AppException(ErrorCode.INVALID_ORDER_AMOUNT);
            }

            // 1. Pessimistic Locking on products to serialize concurrent stock deductions
            Set<String> productIds = order.getOrderDetails().stream()
                    .map(detail -> detail.getProduct().getId())
                    .collect(Collectors.toSet());

            Map<String, Product> lockedProductMap = productRepository.findAllByIdWithLock(productIds).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p));

            // Validate stock availability under lock
            for (OrderDetail detail : order.getOrderDetails()) {
                Product product = lockedProductMap.getOrDefault(detail.getProduct().getId(), detail.getProduct());
                if (product == null || product.getStock() < detail.getQuantity()) {
                    throw new AppException(ErrorCode.PRODUCT_OUT_OF_STOCK,
                            "Sản phẩm " + (product != null ? product.getName() : "") + " đã hết hoặc không đủ tồn kho");
                }
            }

            // =========================

            int actualUsedPoints = 0;
            BigDecimal pointDiscount = BigDecimal.ZERO;

            CustomerProfile lockedProfile = null;
            if (profile != null) {
                lockedProfile = customerProfileRepository.findByIdWithLock(profile.getId()).orElse(profile);
            }

            if (lockedProfile != null && request.getUsedPoints() != null && request.getUsedPoints() > 0) {

                PointDiscountResult result = discountService.applyPointDiscount(
                        lockedProfile,
                        request.getUsedPoints(),
                        orderFinalAmount);

                pointDiscount = result.getDiscountAmount();
                actualUsedPoints = result.getUsedPoints();

                // Lưu lịch sử dùng điểm chống trùng
                LoyaltyTransaction redeemTx = LoyaltyTransaction.builder()
                        .customerProfile(lockedProfile)
                        .referenceId(order.getId())
                        .transactionType("REDEEM")
                        .pointsChange(-actualUsedPoints)
                        .balanceAfter(lockedProfile.getLoyaltyPoints())
                        .idempotencyKey("REDEEM-" + order.getId())
                        .note("Trừ điểm tích lũy đơn " + order.getOrderCode())
                        .build();
                loyaltyTransactionRepository.save(redeemTx);
            }

            // =========================

            BigDecimal paymentAmount = orderFinalAmount.subtract(pointDiscount);

            if (paymentAmount.compareTo(BigDecimal.ZERO) < 0) {
                paymentAmount = BigDecimal.ZERO;
            }

            // =========================

            if (request.getMethod() == PaymentMethod.CASH) {

                if (request.getCashReceived() == null ||
                        request.getCashReceived().compareTo(paymentAmount) < 0) {

                    throw new AppException(ErrorCode.INVALID_PAYMENT_AMOUNT,
                            "Khách đưa không đủ tiền");
                }
            }

            // =========================

            BigDecimal changeAmount = BigDecimal.ZERO;

            if (request.getMethod() == PaymentMethod.CASH) {
                changeAmount = request.getCashReceived().subtract(paymentAmount);
            }

            // =========================

            int earnedPoints = 0;
            int remainingPoints = 0;

            if (lockedProfile != null && paymentAmount.compareTo(BigDecimal.ZERO) > 0) {

                earnedPoints = paymentAmount
                        .multiply(BigDecimal.valueOf(0.01))
                        .setScale(0, RoundingMode.DOWN)
                        .intValue();

                if (earnedPoints > 0) {
                    lockedProfile.setLoyaltyPoints(lockedProfile.getLoyaltyPoints() + earnedPoints);
                    customerProfileRepository.save(lockedProfile);

                    LoyaltyTransaction earnTx = LoyaltyTransaction.builder()
                            .customerProfile(lockedProfile)
                            .referenceId(order.getId())
                            .transactionType("EARN")
                            .pointsChange(earnedPoints)
                            .balanceAfter(lockedProfile.getLoyaltyPoints())
                            .idempotencyKey("EARN-" + order.getId())
                            .note("Tích điểm 1% đơn " + order.getOrderCode())
                            .build();
                    loyaltyTransactionRepository.save(earnTx);
                }

                remainingPoints = lockedProfile.getLoyaltyPoints();
            }

            // =========================

            Payment payment = Payment.builder()
                    .order(order)
                    .user(user)
                    .amount(paymentAmount)
                    .pointDiscount(pointDiscount)
                    .cashReceived(request.getCashReceived())
                    .changeAmount(changeAmount)
                    .method(request.getMethod())
                    .status(PaymentStatus.SUCCESS)
                    .paymentDate(LocalDateTime.now())
                    .transactionId(generateTransactionId())
                    .idempotencyKey(request.getIdempotencyKey())
                    .note(request.getNote())
                    .earnedPoints(earnedPoints)
                    .usedPoints(actualUsedPoints)
                    .build();

            payment = paymentRepository.save(payment);

            // =========================
            // Deduct inventory under lock, record StockMovement, and collect InventoryUpdated payloads
            List<Map<String, Object>> stockEvents = new ArrayList<>();
            for (OrderDetail detail : order.getOrderDetails()) {
                Product product = lockedProductMap.getOrDefault(detail.getProduct().getId(), detail.getProduct());
                int beforeStock = product.getStock();
                product.deductStock(detail.getQuantity());
                productRepository.save(product);

                StockMovement movement = StockMovement.builder()
                        .product(product)
                        .movementType("SALE")
                        .referenceId(order.getId())
                        .quantityBefore(beforeStock)
                        .quantityChange(-detail.getQuantity())
                        .quantityAfter(product.getStock())
                        .note("Bán hàng POS - Đơn " + order.getOrderCode())
                        .createdBy(user != null ? user.getId() : null)
                        .build();
                stockMovementRepository.save(movement);

                Map<String, Object> invPayload = new java.util.LinkedHashMap<>();
                invPayload.put("productId", product.getId());
                invPayload.put("productCode", product.getProductCode());
                invPayload.put("productName", product.getName());
                invPayload.put("movementType", "SALE");
                invPayload.put("beforeStock", beforeStock);
                invPayload.put("quantityChange", -detail.getQuantity());
                invPayload.put("afterStock", product.getStock());
                invPayload.put("referenceId", order.getId());
                stockEvents.add(invPayload);
            }
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);

            // =========================

            PaymentResponse response = paymentMapper.toPaymentResponse(payment);

            response.setEarnedPoints(earnedPoints);
            response.setRemainingPoints(remainingPoints);
            response.setUsedPoints(actualUsedPoints);

            // Transactional Outbox: Emit PaymentCompleted, OrderCompleted, InventoryUpdated, LoyaltyPointsEarned atomically
            if (outboxPublisher != null) {
                Map<String, Object> paymentPayload = new java.util.LinkedHashMap<>();
                paymentPayload.put("paymentId", payment.getId());
                paymentPayload.put("orderId", order.getId());
                paymentPayload.put("orderCode", order.getOrderCode());
                paymentPayload.put("amount", payment.getAmount());
                paymentPayload.put("finalAmount", response.getFinalAmount());
                paymentPayload.put("method", payment.getMethod() != null ? payment.getMethod().name() : null);
                paymentPayload.put("transactionId", payment.getTransactionId());
                paymentPayload.put("userId", user != null ? user.getId() : null);
                paymentPayload.put("userPhone", user != null ? user.getPhone() : null);
                paymentPayload.put("earnedPoints", earnedPoints);
                paymentPayload.put("usedPoints", actualUsedPoints);
                outboxPublisher.publishInCurrentTransaction(
                        "PAYMENT",
                        payment.getId(),
                        PosEventType.PAYMENT_COMPLETED,
                        "order:" + order.getId(),
                        order.getOrderCode(),
                        paymentPayload);

                Map<String, Object> orderCompletedPayload = new java.util.LinkedHashMap<>();
                orderCompletedPayload.put("orderId", order.getId());
                orderCompletedPayload.put("orderCode", order.getOrderCode());
                orderCompletedPayload.put("paymentId", payment.getId());
                orderCompletedPayload.put("amount", payment.getAmount());
                orderCompletedPayload.put("status", OrderStatus.PAID.name());
                outboxPublisher.publishInCurrentTransaction(
                        "ORDER",
                        order.getId(),
                        PosEventType.ORDER_COMPLETED,
                        "order:" + order.getId(),
                        order.getOrderCode(),
                        orderCompletedPayload);

                for (Map<String, Object> invPayload : stockEvents) {
                    String pid = String.valueOf(invPayload.get("productId"));
                    outboxPublisher.publishInCurrentTransaction(
                            "INVENTORY",
                            pid,
                            PosEventType.INVENTORY_UPDATED,
                            "product:" + pid,
                            order.getOrderCode(),
                            invPayload);
                }

                if (earnedPoints > 0 && lockedProfile != null) {
                    Map<String, Object> loyaltyPayload = new java.util.LinkedHashMap<>();
                    loyaltyPayload.put("customerProfileId", lockedProfile.getId());
                    loyaltyPayload.put("userId", user != null ? user.getId() : null);
                    loyaltyPayload.put("userPhone", user != null ? user.getPhone() : null);
                    loyaltyPayload.put("orderId", order.getId());
                    loyaltyPayload.put("orderCode", order.getOrderCode());
                    loyaltyPayload.put("pointsEarned", earnedPoints);
                    loyaltyPayload.put("balanceAfter", remainingPoints);
                    outboxPublisher.publishInCurrentTransaction(
                            "LOYALTY",
                            lockedProfile.getId(),
                            PosEventType.LOYALTY_POINTS_EARNED,
                            "customer:" + lockedProfile.getId(),
                            order.getOrderCode(),
                            loyaltyPayload);
                }
            }

            if (cacheInvalidationService != null) {
                cacheInvalidationService.evictAfterSaleOrRefund();
            }

            if (businessMetrics != null) {
                businessMetrics.recordPaymentCompleted(payment.getMethod(), payment.getAmount(), "POS");
                for (Map<String, Object> invPayload : stockEvents) {
                    Object deltaObj = invPayload.get("quantityChange");
                    if (deltaObj instanceof Number n) {
                        businessMetrics.recordInventoryAdjustment("SALE", n.intValue());
                    }
                }
            }

            return response;
        } catch (RuntimeException ex) {
            if (businessMetrics != null) {
                businessMetrics.recordPaymentFailed(request.getMethod(), ex.getMessage(), "POS");
            }
            publishPaymentFailedSafely(order, request.getMethod(), ex.getMessage());
            throw ex;
        }
    }

    private void publishPaymentFailedSafely(Orders order, PaymentMethod method, String reason) {
        if (outboxPublisher == null || order == null) {
            return;
        }
        try {
            Map<String, Object> failPayload = new java.util.LinkedHashMap<>();
            failPayload.put("orderId", order.getId());
            failPayload.put("orderCode", order.getOrderCode());
            failPayload.put("method", method != null ? method.name() : null);
            failPayload.put("reason", reason != null ? reason : "Payment processing failed");
            outboxPublisher.publishFailureInNewTransaction(
                    "PAYMENT",
                    order.getId(),
                    PosEventType.PAYMENT_FAILED,
                    "order:" + order.getId(),
                    order.getOrderCode(),
                    failPayload);
        } catch (Exception loggingEx) {
            log.warn("Could not record PaymentFailed outbox event for order {}: {}", order.getId(), loggingEx.getMessage());
        }
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public PaymentResponse getByOrderId(String orderId) {
        Payment payment = paymentRepository.findByOrder_Id(orderId);
        if (payment == null) {
            throw new AppException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return paymentMapper.toPaymentResponse(payment);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public PaymentResponse getPaymentById(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        return paymentMapper.toPaymentResponse(payment);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public Page<PaymentResponse> getPayments(
            int page,
            int size,
            String fromDate,
            String toDate,
            String status,
            String method,
            String keyword) {

        LocalDateTime from = null;
        LocalDateTime to = null;

        if (fromDate != null && !fromDate.isBlank()) {
            from = LocalDate.parse(fromDate).atStartOfDay();
        }

        if (toDate != null && !toDate.isBlank()) {
            to = LocalDate.parse(toDate).atTime(23, 59, 59, 999999999);
        }

        PaymentStatus paymentStatus = null;
        if (status != null && !status.isBlank()) {
            paymentStatus = PaymentStatus.valueOf(status);
        }

        PaymentMethod paymentMethod = null;
        if (method != null && !method.isBlank()) {
            paymentMethod = PaymentMethod.valueOf(method);
        }

        Pageable pageable = PageRequest.of(page, size);

        return paymentRepository.filterPayments(
                from,
                to,
                paymentStatus,
                paymentMethod,
                keyword,
                pageable).map(paymentMapper::toPaymentResponse);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public PaymentSummaryResponse getPaymentSummary(
            String fromDate,
            String toDate,
            String status,
            String method,
            String keyword) {
        LocalDateTime from = null;
        LocalDateTime to = null;

        if (fromDate != null && !fromDate.isBlank()) {
            from = LocalDate.parse(fromDate).atStartOfDay();
        }

        if (toDate != null && !toDate.isBlank()) {
            to = LocalDate.parse(toDate).atTime(23, 59, 59, 999999999);
        }

        PaymentStatus paymentStatus = null;
        if (status != null && !status.isBlank()) {
            paymentStatus = PaymentStatus.valueOf(status);
        }

        PaymentMethod paymentMethod = null;
        if (method != null && !method.isBlank()) {
            paymentMethod = PaymentMethod.valueOf(method);
        }

        Object statsObj = paymentRepository.getPaymentSummaryStats(
                from,
                to,
                paymentStatus,
                paymentMethod,
                keyword);

        Object[] row = null;
        if (statsObj instanceof Object[]) {
            row = (Object[]) statsObj;
        } else if (statsObj instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Object[]) {
                row = (Object[]) first;
            }
        }

        if (row != null && row.length >= 5) {
            return PaymentSummaryResponse.builder()
                    .totalTransactions(row[0] != null ? ((Number) row[0]).longValue() : 0L)
                    .totalRevenue(CurrencyUtils.toBigDecimal(row[1]))
                    .totalCash(CurrencyUtils.toBigDecimal(row[2]))
                    .totalDigital(CurrencyUtils.toBigDecimal(row[3]))
                    .totalRefunded(CurrencyUtils.toBigDecimal(row[4]))
                    .build();
        }

        return PaymentSummaryResponse.builder()
                .totalTransactions(0L)
                .totalRevenue(BigDecimal.ZERO)
                .totalCash(BigDecimal.ZERO)
                .totalDigital(BigDecimal.ZERO)
                .totalRefunded(BigDecimal.ZERO)
                .build();
    }

    // --------
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    @Transactional
    public PaymentResponse refundItems(String paymentId, RefundRequest request) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        Orders order = payment.getOrder();

        BigDecimal refundAmount = BigDecimal.ZERO;

        for (RefundItemRequest itemReq : request.getItems()) {

            OrderDetail detail = order.getOrderDetails().stream()
                    .filter(d -> d.getProduct().getId().equals(itemReq.getProductId()))
                    .findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

            // validate số lượng refund còn lại
            int alreadyRefunded = detail.getRefundedQuantity();
            int remainingRefundable = detail.getQuantity() - alreadyRefunded;

            if (itemReq.getQuantity() <= 0 || itemReq.getQuantity() > remainingRefundable) {
                throw new AppException(ErrorCode.INVALID_REFUND_AMOUNT,
                        "Số lượng hoàn (" + itemReq.getQuantity() + ") vượt quá số lượng còn lại có thể hoàn ("
                                + remainingRefundable + ")");
            }

            // cập nhật số lượng đã hoàn trên chi tiết đơn
            detail.setRefundedQuantity(alreadyRefunded + itemReq.getQuantity());

            // 💰 tính tiền refund
            BigDecimal itemRefund = detail.getProduct().getPrice()
                    .multiply(BigDecimal.valueOf(itemReq.getQuantity()));

            refundAmount = refundAmount.add(itemRefund);

            // 📦 trả lại stock an toàn và ghi nhận StockMovement
            Product product = productRepository.findByIdWithLock(detail.getProduct().getId())
                    .orElse(detail.getProduct());
            int beforeStock = product.getStock();
            product.addStock(itemReq.getQuantity());
            productRepository.save(product);

            if (stockMovementRepository != null) {
                StockMovement movement = StockMovement.builder()
                        .product(product)
                        .movementType("REFUND")
                        .referenceId(payment.getId())
                        .quantityBefore(beforeStock)
                        .quantityChange(+itemReq.getQuantity())
                        .quantityAfter(product.getStock())
                        .note("Hoàn trả món POS - Đơn " + order.getOrderCode())
                        .build();
                stockMovementRepository.save(movement);
            }

            if (outboxPublisher != null) {
                Map<String, Object> invPayload = new java.util.LinkedHashMap<>();
                invPayload.put("productId", product.getId());
                invPayload.put("productCode", product.getProductCode());
                invPayload.put("productName", product.getName());
                invPayload.put("movementType", "REFUND");
                invPayload.put("beforeStock", beforeStock);
                invPayload.put("quantityChange", itemReq.getQuantity());
                invPayload.put("afterStock", product.getStock());
                invPayload.put("referenceId", payment.getId());
                outboxPublisher.publishInCurrentTransaction(
                        "INVENTORY",
                        product.getId(),
                        PosEventType.INVENTORY_UPDATED,
                        "product:" + product.getId(),
                        order.getOrderCode(),
                        invPayload);
            }
            if (businessMetrics != null) {
                businessMetrics.recordInventoryAdjustment("REFUND", itemReq.getQuantity());
            }
        }

        orderRepository.save(order);

        // ❌ check không vượt quá tiền thanh toán
        BigDecimal totalRefunded = payment.getTotalRefunded() == null
                ? refundAmount
                : payment.getTotalRefunded().add(refundAmount);

        if (totalRefunded.compareTo(payment.getAmount()) > 0) {
            throw new AppException(ErrorCode.INVALID_REFUND_AMOUNT);
        }

        // 🧾 lưu lịch sử
        List<RefundRecord> history = new ArrayList<>();
        if (payment.getRefundHistoryJson() != null) {
            try {
                history = Arrays.asList(objectMapper.readValue(
                        payment.getRefundHistoryJson(),
                        RefundRecord[].class));
                history = new ArrayList<>(history);
            } catch (Exception ignored) {
            }
        }

        history.add(RefundRecord.builder()
                .amount(refundAmount)
                .reason(request.getReason())
                .date(LocalDateTime.now())
                .build());

        payment.setTotalRefunded(totalRefunded);
        payment.setRefundHistoryJson(writeAsJson(history));

        // ✅ nếu refund full
        if (totalRefunded.compareTo(payment.getAmount()) == 0) {
            payment.setStatus(PaymentStatus.REFUNDED);
        } else {
            payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }

        paymentRepository.save(payment);

        if (cacheInvalidationService != null) {
            cacheInvalidationService.evictAfterSaleOrRefund();
        }

        return paymentMapper.toPaymentResponse(payment);
    }

    private String writeAsJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public List<RefundRecord> getRefundHistory(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getRefundHistoryJson() == null) {
            return List.of();
        }

        try {
            return Arrays.asList(objectMapper.readValue(payment.getRefundHistoryJson(), RefundRecord[].class));
        } catch (Exception e) {
            throw new AppException(ErrorCode.JSON_PARSE_ERROR);
        }
    }

    // ----
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    @Transactional(readOnly = true)
    public ResponseEntity<String> exportInvoiceHtml(String paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "Guest";

        Orders order = payment.getOrder();

        PaymentResponse paymentRes = paymentMapper.toPaymentResponse(payment);
        OrderResponse orderRes = orderMapper.toResponse(order);

        String html = ThermalReceiptRenderer.render(paymentRes, orderRes, username);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    // ------------------------ Helper methods ------------------------

    private void validateCustomerAccess(String targetUserId) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        boolean isStaff = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_MANAGE")
                        || a.getAuthority().equals("ROLE_EMPLOYEE")
                        || a.getAuthority().equals("ADMIN")
                        || a.getAuthority().equals("MANAGE")
                        || a.getAuthority().equals("EMPLOYEE"));
        if (!isStaff) {
            String currentUsername = auth.getName();
            User caller = userRepository.findByUsername(currentUsername)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            if (!caller.getId().equals(targetUserId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED,
                        "Bạn không có quyền truy cập dữ liệu của khách hàng khác");
            }
        }
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE') or hasRole('EMPLOYEE') or hasRole('CUSTOMER')")
    public Page<PaymentResponse> getPaymentsByUserId(String userId, int page, int size) {
        validateCustomerAccess(userId);
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size);
        return paymentRepository.findByUserId(userId, pageable)
                .map(paymentMapper::toPaymentResponse);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE') or hasRole('EMPLOYEE') or hasRole('CUSTOMER')")
    public CustomerHistoryResponse getCustomerHistoryStats(String userId) {
        validateCustomerAccess(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Object statsObj = paymentRepository.getCustomerSpendingStats(userId);
        long totalOrders = 0L;
        BigDecimal totalSpent = BigDecimal.ZERO;
        LocalDateTime lastPurchaseDate = null;

        Object[] row = null;
        if (statsObj instanceof Object[]) {
            row = (Object[]) statsObj;
        } else if (statsObj instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Object[]) {
                row = (Object[]) first;
            }
        }

        if (row != null && row.length >= 3) {
            totalOrders = row[0] != null ? ((Number) row[0]).longValue() : 0L;
            totalSpent = CurrencyUtils.toBigDecimal(row[1]);
            lastPurchaseDate = row[2] != null ? (LocalDateTime) row[2] : null;
        }

        int loyaltyPoints = 0;
        String membershipLevel = "NONE";
        if (user.getCustomerProfile() != null) {
            loyaltyPoints = user.getCustomerProfile().getLoyaltyPoints();
            membershipLevel = user.getCustomerProfile().getMembershipLevel() != null
                    ? user.getCustomerProfile().getMembershipLevel()
                    : "NONE";
        }

        return CustomerHistoryResponse.builder()
                .totalOrders(totalOrders)
                .totalSpent(totalSpent)
                .lastPurchaseDate(lastPurchaseDate)
                .loyaltyPoints(loyaltyPoints)
                .membershipLevel(membershipLevel)
                .build();
    }

    private String generateTransactionId() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String randomPart = java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "TXN-" + datePart + "-" + randomPart;
    }
}
