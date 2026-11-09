package com.possystem.backend.report.service;

import com.possystem.backend.report.dto.DailyReportResponse;
import com.possystem.backend.report.dto.DashboardReportResponse;
import com.possystem.backend.report.dto.MonthlyReportResponse;
import com.possystem.backend.report.dto.ProductSalesReportResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

public interface ReportService {
    DailyReportResponse generateDailyReport(LocalDate date);
    MonthlyReportResponse generateMonthlyReport(YearMonth month);
    DashboardReportResponse getDashboardReport();
    List<ProductSalesReportResponse> getProductReport(
            LocalDateTime from,
            LocalDateTime to,
            String keyword,
            String categoryId
    );
}
