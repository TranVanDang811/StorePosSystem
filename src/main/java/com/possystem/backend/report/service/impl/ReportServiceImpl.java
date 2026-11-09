package com.possystem.backend.report.service.impl;

import com.possystem.backend.common.enums.PaymentMethod;
import com.possystem.backend.common.enums.OrderStatus;
import com.possystem.backend.common.enums.PaymentStatus;
import com.possystem.backend.common.util.mapper.ReportMapper;
import com.possystem.backend.order.entity.Orders;
import com.possystem.backend.order.repository.OrderRepository;
import com.possystem.backend.payment.entity.Payment;
import com.possystem.backend.payment.repository.PaymentRepository;
import com.possystem.backend.report.dto.*;
import com.possystem.backend.report.repository.ReportRepository;
import com.possystem.backend.report.service.ReportService;
import com.possystem.backend.importorder.repository.ImportOrderRepository;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.user.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReportServiceImpl implements ReportService {
    OrderRepository orderRepository;
    PaymentRepository paymentRepository;
    UserRepository userRepository;
    ReportMapper reportMapper;
    ReportRepository reportRepository;
    ProductRepository productRepository;
    ImportOrderRepository importOrderRepository;

    public List<ProductSalesReportResponse> getProductReport(
            LocalDateTime from,
            LocalDateTime to,
            String keyword,
            String categoryName
    ) {
        List<Object[]> rows = reportRepository.getProductSalesReport(from, to, keyword, categoryName);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        return rows.stream().map(row -> {
            String productCode = row[0] != null ? row[0].toString() : "";
            String productName = row[1] != null ? row[1].toString() : "";
            String category = row[2] != null ? row[2].toString() : "Chưa phân loại";
            Long quantity = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            BigDecimal revenue = toBigDecimal(row[4]);

            return ProductSalesReportResponse.builder()
                    .productCode(productCode)
                    .productName(productName)
                    .categoryName(category)
                    .totalQuantity(quantity)
                    .totalRevenue(revenue)
                    .build();
        }).toList();
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    @Override
    public DailyReportResponse generateDailyReport(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        List<Orders> orders = orderRepository.findAllByCreatedAtBetween(start, end);

        List<Payment> payments = paymentRepository.findByPaymentDateBetween(start, end);

        BigDecimal totalRevenue = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .map(Payment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);


        BigDecimal totalRefund = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.REFUNDED)
                .map(Payment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalOrders = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.PAID)
                .count();

        Map<PaymentMethod, BigDecimal> revenueByMethod = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .collect(Collectors.groupingBy(
                        Payment::getMethod,
                        Collectors.mapping(Payment::getAmount,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                ));

        List<Object[]> topProductData = orderRepository.findTopSellingProducts(date);

        List<TopProductDTO> topProducts =
                reportMapper.toTopProductDTOList(topProductData);

        return DailyReportResponse.builder()
                .date(date)
                .totalRevenue(totalRevenue)
                .totalRefund(totalRefund)
                .totalOrders(totalOrders)
                .revenueByMethod(revenueByMethod)
                .topProducts(topProducts)
                .build();
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    @Override
    public MonthlyReportResponse generateMonthlyReport( YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();

        List<Orders> orders = orderRepository.findByCreatedAtBetween(start, end);

        List<Payment> payments = paymentRepository.findByPaymentDateBetween(start, end);

        BigDecimal totalRevenue = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRefund = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.REFUNDED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalOrders = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.PAID)
                .count();

        Map<PaymentMethod, BigDecimal> revenueByMethod = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .collect(Collectors.groupingBy(
                        Payment::getMethod,
                        Collectors.mapping(Payment::getAmount,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                ));

        List<Object[]> topProductData = orderRepository.findTopSellingProductsByMonth(month);
        List<TopProductDTO> topProducts =
                reportMapper.toTopProductDTOList(topProductData);

        return MonthlyReportResponse.builder()
                .month(month)
                .totalRevenue(totalRevenue)
                .totalRefund(totalRefund)
                .totalOrders(totalOrders)
                .revenueByMethod(revenueByMethod)
                .topProducts(topProducts)
                .build();
    }
    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public DashboardReportResponse getDashboardReport() {

        LocalDate today = LocalDate.now();

        LocalDateTime startDay = today.atStartOfDay();
        LocalDateTime endDay = today.plusDays(1).atStartOfDay();

        LocalDateTime startWeek = today.minusDays(6).atStartOfDay();

        LocalDateTime startMonth = today.withDayOfMonth(1).atStartOfDay();

        // customers
        long memberCustomers =
                paymentRepository.countMemberCustomersToday(startDay, endDay);

        long guestCustomers =
                paymentRepository.countGuestCustomersToday(startDay, endDay);

        long totalCustomersToday = memberCustomers + guestCustomers;

        long newMembersToday =
                userRepository.countNewMembersToday(startDay, endDay);

        // revenue
        BigDecimal todayRevenue =
                paymentRepository.getRevenueBetween(startDay, endDay);
        if (todayRevenue == null) todayRevenue = BigDecimal.ZERO;

        BigDecimal weekRevenue =
                paymentRepository.getRevenueBetween(startWeek, endDay);
        if (weekRevenue == null) weekRevenue = BigDecimal.ZERO;

        BigDecimal monthRevenue =
                paymentRepository.getRevenueBetween(startMonth, endDay);
        if (monthRevenue == null) monthRevenue = BigDecimal.ZERO;

        // orders today
        long todayOrders =
                orderRepository.countByCreatedAtBetween(startDay, endDay);

        // revenue last 7 days
        List<BigDecimal> revenueLast7Days = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {

            LocalDateTime start = today.minusDays(i).atStartOfDay();
            LocalDateTime end = today.minusDays(i - 1).atStartOfDay();

            BigDecimal revenue =
                    paymentRepository.getRevenueBetween(start, end);

            revenueLast7Days.add(revenue == null ? BigDecimal.ZERO : revenue);
        }

        // top products today
        List<Object[]> topProductData =
                orderRepository.findTopSellingProducts(today);

        List<TopProductDTO> topProducts =
                reportMapper.toTopProductDTOList(topProductData);

        // recent orders
        List<Orders> recentOrderEntities =
                orderRepository.findTop5ByOrderByCreatedAtDesc();

        List<RecentOrderDTO> recentOrders = recentOrderEntities.stream()
                .map(o -> new RecentOrderDTO(
                        o.getOrderCode(),
                        o.getFinalAmount(),
                        o.getStatus(),
                        o.getCreatedAt()
                ))
                .toList();

        // inventory alerts
        long outOfStockCount = productRepository.countOutOfStock();
        long lowStockCount = productRepository.countLowStock(5);

        // import orders alerts (pending & overdue > 3 days)
        long pendingImportCount = importOrderRepository.countPendingImportOrders();
        long overdueImportCount = importOrderRepository.countOverdueImportOrders(LocalDateTime.now().minusDays(3));

        return DashboardReportResponse.builder()
                .totalCustomersToday(totalCustomersToday)
                .newMembersToday(newMembersToday)
                .todayRevenue(todayRevenue)
                .weekRevenue(weekRevenue)
                .monthRevenue(monthRevenue)
                .todayOrders(todayOrders)
                .revenueLast7Days(revenueLast7Days)
                .topProductsToday(topProducts)
                .recentOrders(recentOrders)
                .outOfStockCount(outOfStockCount)
                .lowStockCount(lowStockCount)
                .pendingImportCount(pendingImportCount)
                .overdueImportCount(overdueImportCount)
                .build();
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
