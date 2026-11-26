package com.possystem.backend.ai.entity;

import com.possystem.backend.common.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Entity
@Table(name = "ai_usage_logs", indexes = {
        @Index(name = "idx_ai_usage_user", columnList = "user_id, created_at"),
        @Index(name = "idx_ai_usage_created", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiUsageLog extends AbstractEntity {

    @Column(name = "user_id", nullable = false, length = 36)
    String userId;

    @Column(name = "conversation_id", length = 36)
    String conversationId;

    @Column(nullable = false, length = 64)
    String provider;

    @Column(nullable = false, length = 64)
    String model;

    @Builder.Default
    @Column(name = "prompt_tokens", nullable = false)
    int promptTokens = 0;

    @Builder.Default
    @Column(name = "completion_tokens", nullable = false)
    int completionTokens = 0;

    @Builder.Default
    @Column(name = "total_tokens", nullable = false)
    int totalTokens = 0;

    @Builder.Default
    @Column(name = "duration_ms", nullable = false)
    long durationMs = 0;

    @Builder.Default
    @Column(name = "cost_estimate_usd", nullable = false, precision = 10, scale = 6)
    BigDecimal costEstimateUsd = BigDecimal.ZERO;

    @Column(nullable = false, length = 32)
    String status; // SUCCESS, ERROR, QUOTA_EXCEEDED

    @Column(name = "error_message", length = 500)
    String errorMessage;
}
