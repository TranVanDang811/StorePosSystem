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
    String userId;
    String fullName;

    BigDecimal totalPrice;      // Tổng tiền ban đầu (chưa giảm)
    String discountCode;        // Mã giảm giá (nếu có)
    BigDecimal discountAmount;  // Số tiền giảm từ mã giảm giá
    Integer usedPoints;         // Điểm khách hàng sử dụng để giảm
    BigDecimal pointDiscount;   // Số tiền được giảm từ điểm (usedPoints * 1.0)
    BigDecimal finalAmount;     // Tổng tiền phải trả cuối cùng sau giảm giá và điểm
    Integer earnedPoints;
    Integer remainingPoints;    // Điểm còn lại của khách hàng sau khi dùng

    OrderStatus status;
    LocalDate createdAt;
    LocalDate updatedAt;

    List<OrderDetailResponse> orderDetails;
}
