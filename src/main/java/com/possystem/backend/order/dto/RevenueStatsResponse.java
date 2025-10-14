package com.possystem.backend.order.dto;

import java.math.BigDecimal;

public record RevenueStatsResponse(
        Long totalOrders,
        BigDecimal totalRevenue,
        Integer totalProductsSold
) {}
