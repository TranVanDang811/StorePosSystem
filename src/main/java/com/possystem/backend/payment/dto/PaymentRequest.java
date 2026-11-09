package com.possystem.backend.payment.dto;

import com.possystem.backend.common.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentRequest {
    String userPhone;

    @PositiveOrZero(message = "Điểm tích lũy sử dụng không được là số âm")
    Integer usedPoints;

    @NotBlank(message = "Mã đơn hàng không được để trống")
    String orderId;

    @DecimalMin(value = "0.0", message = "Tiền khách đưa không được là số âm")
    BigDecimal cashReceived;

    @NotNull(message = "Phương thức thanh toán không được để trống")
    PaymentMethod method;

    String note;
}
