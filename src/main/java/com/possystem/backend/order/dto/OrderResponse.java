package com.possystem.backend.order.dto;

import com.possystem.backend.common.enums.OrderStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderResponse {
    String id;
    String orderCode;
    BigDecimal totalPrice;
    String discountCode;
    BigDecimal discountAmount;
    BigDecimal finalAmount;
    OrderStatus status;
    LocalDate createdAt;
    LocalDate updatedAt;
    List<OrderDetailResponse> orderDetails;
}
