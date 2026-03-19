package com.possystem.backend.payment.dto;

import com.possystem.backend.common.enums.PaymentMethod;
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
    Integer usedPoints;
    String orderId;
    BigDecimal cashReceived;
    PaymentMethod method;
    String note;
}
