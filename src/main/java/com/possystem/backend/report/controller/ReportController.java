package com.possystem.backend.report.controller;

import com.possystem.backend.common.response.ApiResponse;
import com.possystem.backend.report.dto.DailyReportResponse;
import com.possystem.backend.report.dto.DashboardReportResponse;
import com.possystem.backend.report.dto.MonthlyReportResponse;
import com.possystem.backend.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;

@Slf4j
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Report Management", description = "APIs for generating daily and monthly sales reports")
public class ReportController {
    ReportService reportService;
    @Operation(
            summary = "Generate a daily report",
            description = "Generate a detailed daily report for a specific date, including total orders, revenue, and payment breakdown."
    )
    @GetMapping("/daily")
    ApiResponse<DailyReportResponse> getDailyReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.<DailyReportResponse>builder()
                .result( reportService.generateDailyReport(date))
                .build();
    }

    @Operation(
            summary = "Generate a monthly report",
            description = "Generate a summarized monthly report for the given month, including total revenue, number of orders, and sales statistics."
    )
    @GetMapping("/monthly")
    ApiResponse<MonthlyReportResponse> getMonthlyReport(
            @RequestParam("month") @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ApiResponse.<MonthlyReportResponse>builder()
                .result(reportService.generateMonthlyReport(month))
                .build();
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardReportResponse> getDashboardReport() {

        return ApiResponse.<DashboardReportResponse>builder()
                .result(reportService.getDashboardReport())
                .build();
    }
}
