package com.possystem.backend.payment.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WebhookResult {
    boolean success;
    boolean isDuplicate;
    String message;
    String idempotencyKey;
    String paymentId;
    String orderId;
}
