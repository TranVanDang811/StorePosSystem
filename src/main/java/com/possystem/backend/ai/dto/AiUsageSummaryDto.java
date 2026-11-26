package com.possystem.backend.ai.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiUsageSummaryDto {
    long todayTokens;
    long monthTokens;
    BigDecimal todayCostUsd;
    BigDecimal monthCostUsd;
    long dailyLimit;
    long monthlyLimit;
    boolean quotaExceeded;
}
