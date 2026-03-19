package com.possystem.backend.payment.dto;

import com.possystem.backend.common.enums.PaymentMethod;
import com.possystem.backend.common.enums.PaymentStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentResponse {

    String id;
    String userPhone;
    String fullName;
    String orderId;

    BigDecimal amount;

    PaymentMethod method;
    PaymentStatus status;

    LocalDateTime paymentDate;
    String transactionId;
    String note;

    BigDecimal pointDiscount;
    BigDecimal finalAmount;

    Integer earnedPoints;
    Integer remainingPoints;
    Integer usedPoints;

    BigDecimal cashReceived;
    BigDecimal changeAmount;
}
