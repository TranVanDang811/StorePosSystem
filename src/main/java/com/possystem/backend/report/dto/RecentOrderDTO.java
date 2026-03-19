package com.possystem.backend.report.dto;

import com.possystem.backend.common.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class RecentOrderDTO {

    String orderCode;
    BigDecimal amount;
    OrderStatus status;
    LocalDateTime createdAt;

}