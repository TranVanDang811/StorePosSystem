package com.possystem.backend.ai.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RevenueComparisonDto {
    LocalDate period1Start;
    LocalDate period1End;
    BigDecimal period1Revenue;
    long period1Orders;

    LocalDate period2Start;
    LocalDate period2End;
    BigDecimal period2Revenue;
    long period2Orders;

    BigDecimal revenueDifference; // period2 - period1
    double revenueGrowthPercentage; // ((period2 - period1) / period1) * 100
    long ordersDifference;
    String trendAnalysis; // "Tăng trưởng mạnh", "Ổn định", "Giảm nhẹ", etc.
}
