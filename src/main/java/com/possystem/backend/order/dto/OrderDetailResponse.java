package com.possystem.backend.order.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderDetailResponse {
    String productId;
    String name;
    BigDecimal price;
    Integer quantity;
    BigDecimal totalPrice;
}
