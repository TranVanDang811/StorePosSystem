package com.possystem.backend.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderRequest {
    String userId;

    @NotEmpty(message = "Danh sách sản phẩm không được để trống")
    @Valid
    List<OrderDetailRequest> orderDetails;

    String discountCode;

    @PositiveOrZero(message = "Điểm tích lũy sử dụng không được là số âm")
    Integer usedPoints;
}
