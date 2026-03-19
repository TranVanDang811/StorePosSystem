package com.possystem.backend.report.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DashboardReportResponse {

    long totalCustomersToday;
    long newMembersToday;

    BigDecimal todayRevenue;
    BigDecimal weekRevenue;
    BigDecimal monthRevenue;

    long todayOrders;

    List<BigDecimal> revenueLast7Days;

    List<TopProductDTO> topProductsToday;

    List<RecentOrderDTO> recentOrders;

}