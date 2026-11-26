package com.possystem.backend.ai.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentSummaryDto {
    LocalDate fromDate;
    LocalDate toDate;
    BigDecimal totalSuccessfulAmount;
    BigDecimal totalRefundAmount;
    long successfulTransactionCount;
    Map<String, BigDecimal> methodBreakdown;
}
