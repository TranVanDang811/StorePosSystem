package com.possystem.backend.ai.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ShiftRevenueDto {
    String shiftCode;
    String status;
    String cashierName;
    String terminalId;
    LocalDateTime startTime;
    LocalDateTime endTime;
    BigDecimal initialCash;
    BigDecimal cashSales;
    BigDecimal totalRevenue;
    long totalOrders;
}
