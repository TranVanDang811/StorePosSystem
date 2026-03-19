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
import com.possystem.backend.report.service.ReportService;
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

        BigDecimal weekRevenue =
                paymentRepository.getRevenueBetween(startWeek, endDay);

        BigDecimal monthRevenue =
                paymentRepository.getRevenueBetween(startMonth, endDay);

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
                .build();
    }
}
