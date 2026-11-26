package com.possystem.backend.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WebhookPaymentPayload {
    String transactionId;
    String orderId;
    BigDecimal amount;
    String paymentMethod; // BANK_TRANSFER, CASH, QR
    String provider;      // VIETQR, VNPAY, MOMO
    String signature;
    String note;
    String status;        // SUCCESS, FAILED
}
