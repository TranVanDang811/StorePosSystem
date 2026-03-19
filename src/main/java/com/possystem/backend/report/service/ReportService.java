package com.possystem.backend.report.service;

import com.possystem.backend.report.dto.DailyReportResponse;
import com.possystem.backend.report.dto.DashboardReportResponse;
import com.possystem.backend.report.dto.MonthlyReportResponse;

import java.time.LocalDate;
import java.time.YearMonth;

public interface ReportService {
    DailyReportResponse generateDailyReport(LocalDate date);
    MonthlyReportResponse generateMonthlyReport(YearMonth month);
    DashboardReportResponse getDashboardReport();
}
