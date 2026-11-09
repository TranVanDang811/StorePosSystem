package com.possystem.backend.order.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderSummaryResponse {
    Long totalOrders;
    Long paidOrders;
    Long pendingOrders;
    Long holdOrders;
    Long canceledOrders;
    BigDecimal totalRevenue;
}

