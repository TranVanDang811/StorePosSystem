package com.possystem.backend.ai.tools;

import com.possystem.backend.ai.dto.*;
import com.possystem.backend.common.enums.PaymentMethod;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.common.enums.ShiftStatus;
import com.possystem.backend.order.repository.OrderRepository;
import com.possystem.backend.payment.repository.PaymentRepository;
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.report.repository.ReportRepository;
import com.possystem.backend.shift.entity.Shift;
import com.possystem.backend.shift.repository.ShiftRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Bounded read-only Business Tools for the AI Business Assistant.
 * Strictly prohibited from executing raw/arbitrary SQL or mutating orders/payments/inventory.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Transactional(readOnly = true)
public class PosBusinessAiTools {

    PaymentRepository paymentRepository;
    OrderRepository orderRepository;
    ProductRepository productRepository;
    ShiftRepository shiftRepository;
    ReportRepository reportRepository;

    /**
     * Tool 1: getSalesSummary
     * Answers: Doanh thu hôm nay? Tổng số đơn hàng hôm nay? Phân tích xu hướng kinh doanh?
     */
    public SalesSummaryDto getSalesSummary(LocalDate fromDate, LocalDate toDate) {
        LocalDate startD = fromDate != null ? fromDate : LocalDate.now();
        LocalDate endD = toDate != null ? toDate : startD;
        if (endD.isBefore(startD)) {
            LocalDate temp = startD;
            startD = endD;
            endD = temp;
        }

        LocalDateTime start = startD.atStartOfDay();
        LocalDateTime end = endD.atTime(LocalTime.MAX);

        BigDecimal revenue = Optional.ofNullable(paymentRepository.getRevenueBetween(start, end))
                .orElse(BigDecimal.ZERO);
        long orders = paymentRepository.countPaidOrders(start, end);
        BigDecimal refund = Optional.ofNullable(paymentRepository.getTotalRefundBetween(start, end))
                .orElse(BigDecimal.ZERO);
        long members = paymentRepository.countMemberCustomersToday(start, end);
        long guests = paymentRepository.countGuestCustomersToday(start, end);

        BigDecimal avgOrderValue = orders > 0
                ? revenue.divide(BigDecimal.valueOf(orders), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, BigDecimal> methodMap = new LinkedHashMap<>();
        List<Object[]> methodRows = paymentRepository.getRevenueByMethodBetween(start, end);
        if (methodRows != null) {
            for (Object[] row : methodRows) {
                PaymentMethod method = (PaymentMethod) row[0];
                BigDecimal amount = (BigDecimal) row[1];
                methodMap.put(method != null ? method.name() : "OTHER", amount != null ? amount : BigDecimal.ZERO);
            }
        }

        return SalesSummaryDto.builder()
                .fromDate(startD)
                .toDate(endD)
                .totalRevenue(revenue)
                .totalOrders(orders)
                .averageOrderValue(avgOrderValue)
                .totalRefund(refund)
                .memberCustomerCount(members)
                .guestCustomerCount(guests)
                .revenueByPaymentMethod(methodMap)
                .build();
    }

    /**
     * Tool 2: getTopSellingProducts
     * Answers: Sản phẩm bán chạy nhất?
     */
    public TopSellingProductsDto getTopSellingProducts(int limit, LocalDate fromDate, LocalDate toDate) {
        int safeLimit = limit <= 0 ? 5 : Math.min(limit, 20);
        LocalDate startD = fromDate != null ? fromDate : LocalDate.now();
        LocalDate endD = toDate != null ? toDate : startD;

        LocalDateTime start = startD.atStartOfDay();
        LocalDateTime end = endD.atTime(LocalTime.MAX);

        List<Object[]> rows = reportRepository.getProductSalesReport(start, end, null, null);
        List<TopSellingProductsDto.ProductItem> items = new ArrayList<>();

        if (rows != null) {
            for (Object[] r : rows) {
                if (items.size() >= safeLimit) break;
                String code = (String) r[0];
                String name = (String) r[1];
                String category = (String) r[2];
                Number qty = (Number) r[3];
                BigDecimal rev = (BigDecimal) r[4];

                items.add(TopSellingProductsDto.ProductItem.builder()
                        .productCode(code)
                        .productName(name)
                        .category(category)
                        .quantitySold(qty != null ? qty.longValue() : 0)
                        .totalRevenue(rev != null ? rev : BigDecimal.ZERO)
                        .build());
            }
        }

        String periodDesc = startD.equals(endD)
                ? "Ngày " + startD
                : "Từ " + startD + " đến " + endD;

        return TopSellingProductsDto.builder()
                .periodDescription(periodDesc)
                .products(items)
                .build();
    }

    /**
     * Tool 3: getInventoryStatus
     * Answers: Sản phẩm sắp hết hàng?
     */
    public InventoryStatusDto getInventoryStatus(Integer lowStockThreshold) {
        int threshold = (lowStockThreshold != null && lowStockThreshold > 0) ? lowStockThreshold : 10;
        long outCount = productRepository.countOutOfStock();
        long lowCount = productRepository.countLowStock(threshold);

        List<Product> allProducts = productRepository.findAll();
        List<InventoryStatusDto.StockItem> outList = new ArrayList<>();
        List<InventoryStatusDto.StockItem> lowList = new ArrayList<>();

        for (Product p : allProducts) {
            if (p.getStatus() == ProductStatus.DISCONTINUED) {
                continue;
            }
            if (p.getStock() <= 0 || p.getStatus() == ProductStatus.OUT_OF_STOCK) {
                outList.add(InventoryStatusDto.StockItem.builder()
                        .productCode(p.getProductCode())
                        .name(p.getName())
                        .currentStock(p.getStock())
                        .unit(p.getUnit())
                        .price(p.getPrice())
                        .build());
            } else if (p.getStock() <= threshold) {
                lowList.add(InventoryStatusDto.StockItem.builder()
                        .productCode(p.getProductCode())
                        .name(p.getName())
                        .currentStock(p.getStock())
                        .unit(p.getUnit())
                        .price(p.getPrice())
                        .build());
            }
        }

        // Sort by lowest stock first
        outList.sort(Comparator.comparingInt(InventoryStatusDto.StockItem::getCurrentStock));
        lowList.sort(Comparator.comparingInt(InventoryStatusDto.StockItem::getCurrentStock));

        return InventoryStatusDto.builder()
                .totalOutOfStockCount(outCount)
                .totalLowStockCount(lowCount)
                .thresholdUsed(threshold)
                .outOfStockProducts(outList.stream().limit(15).collect(Collectors.toList()))
                .lowStockProducts(lowList.stream().limit(15).collect(Collectors.toList()))
                .build();
    }

    /**
     * Tool 4: getShiftRevenue
     * Answers: Doanh thu theo ca?
     */
    public ShiftRevenueDto getShiftRevenue(String terminalId, String shiftCode) {
        Shift shift = null;
        if (shiftCode != null && !shiftCode.isBlank()) {
            shift = shiftRepository.findByShiftCode(shiftCode).orElse(null);
        } else if (terminalId != null && !terminalId.isBlank()) {
            shift = shiftRepository.findByStatusAndTerminalId(ShiftStatus.OPEN, terminalId).orElse(null);
        } else {
            shift = shiftRepository.findByStatus(ShiftStatus.OPEN).orElse(null);
        }

        // Fallback to latest closed shift if no open shift found
        if (shift == null) {
            shift = shiftRepository.findTopByStatusOrderByEndTimeDesc(ShiftStatus.CLOSED).orElse(null);
        }

        if (shift == null) {
            return ShiftRevenueDto.builder()
                    .shiftCode("N/A")
                    .status("NO_SHIFT")
                    .cashierName("N/A")
                    .terminalId(terminalId != null ? terminalId : "POS-01")
                    .startTime(LocalDateTime.now())
                    .initialCash(BigDecimal.ZERO)
                    .cashSales(BigDecimal.ZERO)
                    .totalRevenue(BigDecimal.ZERO)
                    .totalOrders(0)
                    .build();
        }

        LocalDateTime shiftStart = shift.getStartTime();
        LocalDateTime shiftEnd = shift.getEndTime() != null ? shift.getEndTime() : LocalDateTime.now();

        BigDecimal cashSales = Optional.ofNullable(paymentRepository.sumCashByShift(shiftStart, shiftEnd))
                .orElse(BigDecimal.ZERO);
        BigDecimal totalRevenue = Optional.ofNullable(paymentRepository.getRevenueBetween(shiftStart, shiftEnd))
                .orElse(BigDecimal.ZERO);
        long orders = paymentRepository.countPaidOrders(shiftStart, shiftEnd);

        String cashierName = shift.getOpenedBy() != null ? shift.getOpenedBy().getFullName()
                : (shift.getClosedBy() != null ? shift.getClosedBy().getFullName() : "Thu ngân");

        return ShiftRevenueDto.builder()
                .shiftCode(shift.getShiftCode())
                .status(shift.getStatus().name())
                .cashierName(cashierName)
                .terminalId(shift.getTerminalId())
                .startTime(shiftStart)
                .endTime(shift.getEndTime())
                .initialCash(shift.getOpeningCash() != null ? shift.getOpeningCash() : BigDecimal.ZERO)
                .cashSales(cashSales)
                .totalRevenue(totalRevenue)
                .totalOrders(orders)
                .build();
    }

    /**
     * Tool 5: compareRevenue
     * Answers: So sánh doanh thu giữa hai khoảng thời gian?
     */
    public RevenueComparisonDto compareRevenue(LocalDate p1Start, LocalDate p1End, LocalDate p2Start, LocalDate p2End) {
        LocalDate period1S = p1Start != null ? p1Start : LocalDate.now().minusDays(7);
        LocalDate period1E = p1End != null ? p1End : period1S;
        LocalDate period2S = p2Start != null ? p2Start : LocalDate.now();
        LocalDate period2E = p2End != null ? p2End : period2S;

        BigDecimal rev1 = Optional.ofNullable(paymentRepository.getRevenueBetween(
                period1S.atStartOfDay(), period1E.atTime(LocalTime.MAX))).orElse(BigDecimal.ZERO);
        long orders1 = paymentRepository.countPaidOrders(period1S.atStartOfDay(), period1E.atTime(LocalTime.MAX));

        BigDecimal rev2 = Optional.ofNullable(paymentRepository.getRevenueBetween(
                period2S.atStartOfDay(), period2E.atTime(LocalTime.MAX))).orElse(BigDecimal.ZERO);
        long orders2 = paymentRepository.countPaidOrders(period2S.atStartOfDay(), period2E.atTime(LocalTime.MAX));

        BigDecimal diffRevenue = rev2.subtract(rev1);
        long diffOrders = orders2 - orders1;

        double growthPct = 0.0;
        if (rev1.compareTo(BigDecimal.ZERO) > 0) {
            growthPct = diffRevenue.divide(rev1, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
        } else if (rev2.compareTo(BigDecimal.ZERO) > 0) {
            growthPct = 100.0;
        }

        String trend;
        if (growthPct >= 15.0) {
            trend = "Tăng trưởng mạnh mẽ (+" + String.format("%.1f", growthPct) + "%)";
        } else if (growthPct > 0.0) {
            trend = "Tăng trưởng nhẹ (+" + String.format("%.1f", growthPct) + "%)";
        } else if (growthPct == 0.0) {
            trend = "Doanh thu ổn định ngang bằng kỳ trước";
        } else if (growthPct > -15.0) {
            trend = "Suy giảm nhẹ (" + String.format("%.1f", growthPct) + "%)";
        } else {
            trend = "Suy giảm đáng kể (" + String.format("%.1f", growthPct) + "%)";
        }

        return RevenueComparisonDto.builder()
                .period1Start(period1S)
                .period1End(period1E)
                .period1Revenue(rev1)
                .period1Orders(orders1)
                .period2Start(period2S)
                .period2End(period2E)
                .period2Revenue(rev2)
                .period2Orders(orders2)
                .revenueDifference(diffRevenue)
                .revenueGrowthPercentage(growthPct)
                .ordersDifference(diffOrders)
                .trendAnalysis(trend)
                .build();
    }

    /**
     * Tool 6: getPaymentSummary
     * Answers: Thống kê chi tiết phương thức thanh toán
     */
    public PaymentSummaryDto getPaymentSummary(LocalDate fromDate, LocalDate toDate) {
        LocalDate startD = fromDate != null ? fromDate : LocalDate.now();
        LocalDate endD = toDate != null ? toDate : startD;

        LocalDateTime start = startD.atStartOfDay();
        LocalDateTime end = endD.atTime(LocalTime.MAX);

        BigDecimal revenue = Optional.ofNullable(paymentRepository.getRevenueBetween(start, end)).orElse(BigDecimal.ZERO);
        BigDecimal refund = Optional.ofNullable(paymentRepository.getTotalRefundBetween(start, end)).orElse(BigDecimal.ZERO);
        long paidCount = paymentRepository.countPaidOrders(start, end);

        Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
        List<Object[]> rows = paymentRepository.getRevenueByMethodBetween(start, end);
        if (rows != null) {
            for (Object[] r : rows) {
                PaymentMethod m = (PaymentMethod) r[0];
                BigDecimal a = (BigDecimal) r[1];
                breakdown.put(m != null ? m.name() : "KHÁC", a != null ? a : BigDecimal.ZERO);
            }
        }

        return PaymentSummaryDto.builder()
                .fromDate(startD)
                .toDate(endD)
                .totalSuccessfulAmount(revenue)
                .totalRefundAmount(refund)
                .successfulTransactionCount(paidCount)
                .methodBreakdown(breakdown)
                .build();
    }

    /**
     * Tool 7: getProfitabilityReport
     * Answers: Sản phẩm có lợi nhuận cao nhất?
     */
    public ProfitabilityReportDto getProfitabilityReport(int limit) {
        int safeLimit = limit <= 0 ? 10 : Math.min(limit, 30);
        List<Product> products = productRepository.findAll();
        List<ProfitabilityReportDto.ProfitItem> items = new ArrayList<>();

        for (Product p : products) {
            if (p.getStatus() == ProductStatus.DISCONTINUED) continue;
            BigDecimal price = p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO;
            BigDecimal cost = p.getCost() != null ? p.getCost() : BigDecimal.ZERO;
            if (price.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal unitProfit = price.subtract(cost);
            double marginPct = unitProfit.divide(price, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();

            String catName = p.getCategory() != null ? p.getCategory().getName() : "Chưa phân loại";

            items.add(ProfitabilityReportDto.ProfitItem.builder()
                    .productCode(p.getProductCode())
                    .name(p.getName())
                    .category(catName)
                    .sellingPrice(price)
                    .costPrice(cost)
                    .unitProfit(unitProfit)
                    .profitMarginPercentage(marginPct)
                    .build());
        }

        // Sort by highest unit profit first, then margin percentage
        items.sort(Comparator.comparing(ProfitabilityReportDto.ProfitItem::getUnitProfit).reversed()
                .thenComparing(Comparator.comparingDouble(ProfitabilityReportDto.ProfitItem::getProfitMarginPercentage).reversed()));

        return ProfitabilityReportDto.builder()
                .topProfitableProducts(items.stream().limit(safeLimit).collect(Collectors.toList()))
                .build();
    }
}
