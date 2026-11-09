package com.possystem.backend.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.possystem.backend.common.enums.OrderStatus;
import com.possystem.backend.common.enums.PaymentMethod;
import com.possystem.backend.common.enums.PaymentStatus;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
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
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.user.dto.CustomerHistoryResponse;
import com.possystem.backend.user.entity.CustomerProfile;
import com.possystem.backend.user.entity.User;
import com.possystem.backend.user.repository.CustomerProfileRepository;
import com.possystem.backend.user.repository.UserRepository;
import jakarta.transaction.Transactional;
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

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {

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

        if (paymentRepository.findByOrder_Id(order.getId()) != null) {
            throw new AppException(ErrorCode.PAYMENT_ALREADY_EXISTS);
        }

        BigDecimal orderFinalAmount = order.getFinalAmount();

        if (orderFinalAmount == null || orderFinalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.INVALID_ORDER_AMOUNT);
        }

        // =========================

        int actualUsedPoints = 0;
        BigDecimal pointDiscount = BigDecimal.ZERO;

        if (profile != null && request.getUsedPoints() != null) {

            PointDiscountResult result = discountService.applyPointDiscount(
                    profile,
                    request.getUsedPoints(),
                    orderFinalAmount);

            pointDiscount = result.getDiscountAmount();
            actualUsedPoints = result.getUsedPoints();
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

        if (profile != null) {

            earnedPoints = paymentAmount
                    .multiply(BigDecimal.valueOf(0.01))
                    .setScale(0, RoundingMode.DOWN)
                    .intValue();

            profile.setLoyaltyPoints(profile.getLoyaltyPoints() + earnedPoints);

            customerProfileRepository.save(profile);

            remainingPoints = profile.getLoyaltyPoints();
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
                .transactionId("TXN-" + System.currentTimeMillis())
                .note(request.getNote())
                .earnedPoints(earnedPoints)
                .usedPoints(actualUsedPoints)
                .build();

        paymentRepository.save(payment);

        // =========================
        for (OrderDetail detail : order.getOrderDetails()) {

            Product product = detail.getProduct();

            int newStock = product.getStock() - detail.getQuantity();

            if (newStock < 0) {
                throw new AppException(ErrorCode.PRODUCT_OUT_OF_STOCK);
            }

            product.setStock(newStock);

            // nếu hết hàng → đổi trạng thái
            if (newStock == 0) {
                product.setStatus(ProductStatus.OUT_OF_STOCK);
            }

            productRepository.save(product);
        }
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        // =========================

        PaymentResponse response = paymentMapper.toPaymentResponse(payment);

        response.setEarnedPoints(earnedPoints);
        response.setRemainingPoints(remainingPoints);
        response.setUsedPoints(actualUsedPoints);

        return response;
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

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
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

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
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
                    .totalRevenue(toBigDecimal(row[1]))
                    .totalCash(toBigDecimal(row[2]))
                    .totalDigital(toBigDecimal(row[3]))
                    .totalRefunded(toBigDecimal(row[4]))
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
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
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
                        "Số lượng hoàn (" + itemReq.getQuantity() + ") vượt quá số lượng còn lại có thể hoàn (" + remainingRefundable + ")");
            }

            // cập nhật số lượng đã hoàn trên chi tiết đơn
            detail.setRefundedQuantity(alreadyRefunded + itemReq.getQuantity());

            // 💰 tính tiền refund
            BigDecimal itemRefund = detail.getProduct().getPrice()
                    .multiply(BigDecimal.valueOf(itemReq.getQuantity()));

            refundAmount = refundAmount.add(itemRefund);

            // 📦 trả lại stock
            Product product = detail.getProduct();
            product.setStock(product.getStock() + itemReq.getQuantity());

            if (product.getStatus() == ProductStatus.OUT_OF_STOCK) {
                product.setStatus(ProductStatus.ACTIVE);
            }

            productRepository.save(product);
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

        return paymentMapper.toPaymentResponse(payment);
    }

    private String writeAsJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
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
    @Transactional
    public ResponseEntity<String> exportInvoiceHtml(String paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        Orders order = payment.getOrder();

        PaymentResponse paymentRes = paymentMapper.toPaymentResponse(payment);
        OrderResponse orderRes = orderMapper.toResponse(order);

        String html = generateThermalHtml(paymentRes, orderRes, username);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    private String generateThermalHtml(PaymentResponse payment, OrderResponse order, String username) {

        StringBuilder items = new StringBuilder();

        for (OrderDetailResponse item : order.getOrderDetails()) {

            BigDecimal subtotal = item.getPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()));

            items.append("""
                    <div class="item">
                        <div class="name">%s</div>
                        <div class="line small">
                            <span>%dx%s</span>
                            <span>%s</span>
                        </div>
                    </div>
                    """.formatted(
                    item.getName(),
                    item.getQuantity(),
                    formatShort(item.getPrice()),
                    formatShort(subtotal)));
        }

        return """
                <html>
                <head>
                    <style>
                        body {
                            font-family: monospace;
                            width: 80mm;
                            margin: 0;
                            padding: 10px;
                            font-size: 13px;
                        }

                        .center {
                            text-align: center;
                        }

                        .line {
                            display: flex;
                            justify-content: space-between;
                        }

                        .small {
                            font-size: 12px;
                        }

                        .name {
                            font-weight: 500;
                        }

                        hr {
                            border-top: 1px dashed black;
                            margin: 6px 0;
                        }

                        .total {
                            font-weight: bold;
                            font-size: 15px;
                        }

                        .section {
                            margin-top: 5px;
                        }

                        .footer {
                            text-align: right;
                            font-size: 12px;
                            margin-top: 10px;
                        }
                    </style>
                </head>

                <body>

                    <!-- HEADER -->
                    <div class="center">
                        <h3>DANG CAP STORE</h3>
                        <div>Hotline: 0123 456 789</div>
                        <div>--------------------------</div>
                    </div>

                    <!-- INFO -->
                    <div class="section small">
                        <div>Date: %s</div>
                        <div>Customer: %s</div>
                    </div>

                    <hr/>

                    <!-- ITEMS -->
                    %s

                    <hr/>

                    <!-- SUMMARY -->
                    <div class="line"><span>Total</span><span>%s</span></div>
                    <div class="line"><span>Discount</span><span>-%s</span></div>
                    <div class="line total"><span>Final</span><span>%s</span></div>

                    <hr/>

                    <!-- PAYMENT -->
                    <div class="line"><span>Method</span><span>%s</span></div>
                    <div class="line"><span>Received</span><span>%s</span></div>
                    <div class="line"><span>Change</span><span>%s</span></div>

                    <hr/>

                    <!-- FOOTER INFO -->
                    <div class="footer">
                        Txn: %s <br/>
                        Staff: %s
                    </div>

                    <hr/>

                    <div class="center small">
                        Thank you <br/>
                        See you again!
                    </div>

                </body>
                </html>
                """.formatted(
                payment.getPaymentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                payment.getFullName() != null ? payment.getFullName() : "Guest",
                items.toString(),

                formatShort(order.getTotalPrice()),

                formatShort(
                        (order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO)
                                .add(payment.getPointDiscount() != null ? payment.getPointDiscount()
                                        : BigDecimal.ZERO)),

                formatShort(payment.getAmount()),
                payment.getMethod() != null ? payment.getMethod().name() : "CASH",

                formatShort(payment.getCashReceived()),
                formatShort(payment.getChangeAmount()),
                payment.getTransactionId(),
                username);
    }

    // ------------------------ Helper methods ------------------------

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE') or hasRole('EMPLOYEE')")
    public Page<PaymentResponse> getPaymentsByUserId(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size);
        return paymentRepository.findByUserId(userId, pageable)
                .map(paymentMapper::toPaymentResponse);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE') or hasRole('EMPLOYEE')")
    public CustomerHistoryResponse getCustomerHistoryStats(String userId) {
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
            totalSpent = toBigDecimal(row[1]);
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

    private String formatShort(BigDecimal amount) {
        if (amount == null)
            return "0";
        return String.format("%,.0f", amount);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

}

    
            

