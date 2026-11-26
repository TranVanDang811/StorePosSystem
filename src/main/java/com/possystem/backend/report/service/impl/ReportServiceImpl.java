package com.possystem.backend.report.service.impl;

import com.possystem.backend.common.cache.CacheNames;
import com.possystem.backend.common.cache.PosCacheService;
import com.possystem.backend.common.enums.PaymentMethod;
import com.possystem.backend.common.util.CurrencyUtils;
import com.possystem.backend.common.util.mapper.ReportMapper;
import com.possystem.backend.importorder.repository.ImportOrderRepository;
import com.possystem.backend.order.entity.Orders;
import com.possystem.backend.order.repository.OrderRepository;
import com.possystem.backend.payment.repository.PaymentRepository;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.report.dto.*;
import com.possystem.backend.report.repository.ReportRepository;
import com.possystem.backend.report.service.ReportService;
import com.possystem.backend.user.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReportServiceImpl implements ReportService {
    OrderRepository orderRepository;
    PaymentRepository paymentRepository;
    UserRepository userRepository;
    ReportMapper reportMapper;
    ReportRepository reportRepository;
    ProductRepository productRepository;
    ImportOrderRepository importOrderRepository;
    PosCacheService posCacheService;

    public List<ProductSalesReportResponse> getProductReport(
            LocalDateTime from,
            LocalDateTime to,
            String keyword,
            String categoryName) {
        String key = String.format("from=%s|to=%s|kw=%s|cat=%s",
                from != null ? from : "-",
                to != null ? to : "-",
                normalize(keyword),
                normalize(categoryName));

        if (posCacheService != null) {
            return posCacheService.getOrLoad(CacheNames.REPORTS_PRODUCT_SALES, key,
                    () -> loadProductReportFromDb(from, to, keyword, categoryName));
        }
        return loadProductReportFromDb(from, to, keyword, categoryName);
    }

    private List<ProductSalesReportResponse> loadProductReportFromDb(
            LocalDateTime from,
            LocalDateTime to,
            String keyword,
            String categoryName) {
        List<Object[]> rows = reportRepository.getProductSalesReport(from, to, keyword, categoryName);
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }

        return new ArrayList<>(rows.stream().map(row -> {
            String productCode = row[0] != null ? row[0].toString() : "";
            String productName = row[1] != null ? row[1].toString() : "";
            String category = row[2] != null ? row[2].toString() : "Chưa phân loại";
            Long quantity = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            BigDecimal revenue = CurrencyUtils.toBigDecimal(row[4]);

            return ProductSalesReportResponse.builder()
                    .productCode(productCode)
                    .productName(productName)
                    .categoryName(category)
                    .totalQuantity(quantity)
                    .totalRevenue(revenue)
                    .build();
        }).toList());
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    @Override
    public DailyReportResponse generateDailyReport(LocalDate date) {
        String key = date.toString();
        if (posCacheService != null) {
            return posCacheService.getOrLoad(CacheNames.REPORTS_DAILY, key, () -> loadDailyReportFromDb(date));
        }
        return loadDailyReportFromDb(date);
    }

    private DailyReportResponse loadDailyReportFromDb(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        BigDecimal totalRevenue = paymentRepository.getRevenueBetween(start, end);
        BigDecimal totalRefund = paymentRepository.getTotalRefundBetween(start, end);
        long totalOrders = paymentRepository.countPaidOrders(start, end);

        Map<PaymentMethod, BigDecimal> revenueByMethod = new LinkedHashMap<>(
                paymentRepository.getRevenueByMethodBetween(start, end)
                        .stream()
                        .collect(Collectors.toMap(
                                row -> (PaymentMethod) row[0],
                                row -> CurrencyUtils.toBigDecimal(row[1]),
                                BigDecimal::add)));

        List<Object[]> topProductData = orderRepository.findTopSellingProducts(date);
        List<TopProductDTO> topProducts = new ArrayList<>(reportMapper.toTopProductDTOList(topProductData));

        return DailyReportResponse.builder()
                .date(date)
                .totalRevenue(totalRevenue != null ? totalRevenue : BigDecimal.ZERO)
                .totalRefund(totalRefund != null ? totalRefund : BigDecimal.ZERO)
                .totalOrders(totalOrders)
                .revenueByMethod(revenueByMethod)
                .topProducts(topProducts)
                .build();
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    @Override
    public MonthlyReportResponse generateMonthlyReport(YearMonth month) {
        String key = month.toString();
        if (posCacheService != null) {
            return posCacheService.getOrLoad(CacheNames.REPORTS_MONTHLY, key, () -> loadMonthlyReportFromDb(month));
        }
        return loadMonthlyReportFromDb(month);
    }

    private MonthlyReportResponse loadMonthlyReportFromDb(YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();

        BigDecimal totalRevenue = paymentRepository.getRevenueBetween(start, end);
        BigDecimal totalRefund = paymentRepository.getTotalRefundBetween(start, end);
        long totalOrders = paymentRepository.countPaidOrders(start, end);

        Map<PaymentMethod, BigDecimal> revenueByMethod = new LinkedHashMap<>(
                paymentRepository.getRevenueByMethodBetween(start, end)
                        .stream()
                        .collect(Collectors.toMap(
                                row -> (PaymentMethod) row[0],
                                row -> CurrencyUtils.toBigDecimal(row[1]),
                                BigDecimal::add)));

        List<Object[]> topProductData = orderRepository.findTopSellingProductsByMonth(month);
        List<TopProductDTO> topProducts = new ArrayList<>(reportMapper.toTopProductDTOList(topProductData));

        return MonthlyReportResponse.builder()
                .month(month)
                .totalRevenue(totalRevenue != null ? totalRevenue : BigDecimal.ZERO)
                .totalRefund(totalRefund != null ? totalRefund : BigDecimal.ZERO)
                .totalOrders(totalOrders)
                .revenueByMethod(revenueByMethod)
                .topProducts(topProducts)
                .build();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public DashboardReportResponse getDashboardReport() {
        String key = "today:" + LocalDate.now();
        if (posCacheService != null) {
            return posCacheService.getOrLoad(CacheNames.REPORTS_DASHBOARD, key, this::loadDashboardReportFromDb);
        }
        return loadDashboardReportFromDb();
    }

    private DashboardReportResponse loadDashboardReportFromDb() {
        LocalDate today = LocalDate.now();

        LocalDateTime startDay = today.atStartOfDay();
        LocalDateTime endDay = today.plusDays(1).atStartOfDay();
        LocalDateTime startWeek = today.minusDays(6).atStartOfDay();
        LocalDateTime startMonth = today.withDayOfMonth(1).atStartOfDay();

        // customers
        long memberCustomers = paymentRepository.countMemberCustomersToday(startDay, endDay);
        long guestCustomers = paymentRepository.countGuestCustomersToday(startDay, endDay);
        long totalCustomersToday = memberCustomers + guestCustomers;
        long newMembersToday = userRepository.countNewMembersToday(startDay, endDay);

        // revenue totals
        BigDecimal todayRevenue = paymentRepository.getRevenueBetween(startDay, endDay);
        if (todayRevenue == null)
            todayRevenue = BigDecimal.ZERO;

        BigDecimal weekRevenue = paymentRepository.getRevenueBetween(startWeek, endDay);
        if (weekRevenue == null)
            weekRevenue = BigDecimal.ZERO;

        BigDecimal monthRevenue = paymentRepository.getRevenueBetween(startMonth, endDay);
        if (monthRevenue == null)
            monthRevenue = BigDecimal.ZERO;

        // orders today
        long todayOrders = orderRepository.countByCreatedAtBetween(startDay, endDay);

        // Optimized: 1 grouped SQL query replaces 7 sequential per-day queries
        Map<LocalDate, BigDecimal> dailyRevenueMap = new HashMap<>();
        List<Object[]> dailyRows = paymentRepository.getDailyRevenueBetween(startWeek, endDay);
        if (dailyRows != null) {
            for (Object[] row : dailyRows) {
                if (row[0] != null) {
                    LocalDate day = parseLocalDate(row[0]);
                    if (day != null) {
                        dailyRevenueMap.put(day, CurrencyUtils.toBigDecimal(row[1]));
                    }
                }
            }
        }

        List<BigDecimal> revenueLast7Days = new ArrayList<>(7);
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            revenueLast7Days.add(dailyRevenueMap.getOrDefault(day, BigDecimal.ZERO));
        }

        // top products today
        List<Object[]> topProductData = orderRepository.findTopSellingProducts(today);
        List<TopProductDTO> topProducts = new ArrayList<>(reportMapper.toTopProductDTOList(topProductData));

        // recent orders
        List<Orders> recentOrderEntities = orderRepository.findTop5ByOrderByCreatedAtDesc();
        List<RecentOrderDTO> recentOrders = new ArrayList<>(recentOrderEntities.stream()
                .map(o -> new RecentOrderDTO(
                        o.getOrderCode(),
                        o.getFinalAmount(),
                        o.getStatus(),
                        o.getCreatedAt()))
                .toList());

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

    private LocalDate parseLocalDate(Object dateObj) {
        if (dateObj instanceof LocalDate ld) {
            return ld;
        }
        if (dateObj instanceof java.sql.Date sd) {
            return sd.toLocalDate();
        }
        try {
            String s = dateObj.toString().trim();
            return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            return null;
        }
    }

    private String normalize(String s) {
        return (s != null && !s.isBlank()) ? s.trim().toLowerCase() : "-";
    }
}
