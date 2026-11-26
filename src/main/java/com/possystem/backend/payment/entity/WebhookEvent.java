package com.possystem.backend.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity lưu vết sự kiện Webhook từ các cổng thanh toán (VietQR, VNPay, MoMo, PayOS).
 * Đảm bảo tính Idempotency (chống xử lý đơn kép khi cổng thanh toán retry nhiều lần).
 */
@Entity
@Table(name = "webhook_events", indexes = {
    @Index(name = "idx_webhook_idempotency", columnList = "idempotency_key", unique = true),
    @Index(name = "idx_webhook_order_id", columnList = "order_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    String idempotencyKey;

    @Column(nullable = false, length = 50)
    String provider; // VIETQR, VNPAY, MOMO, PAYOS, ZALOPAY

    @Column(name = "order_id", length = 64)
    String orderId;

    @Column(precision = 15, scale = 2)
    BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    WebhookStatus status;

    @Column(columnDefinition = "TEXT")
    String payload;

    @Column(name = "response_body", columnDefinition = "TEXT")
    String responseBody;

    @Column(name = "retry_count")
    @Builder.Default
    Integer retryCount = 0;

    @Column(name = "processed_at")
    LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public enum WebhookStatus {
        PENDING,
        PROCESSED,
        DUPLICATE_IGNORED,
        FAILED,
        RECONCILED
    }
}
