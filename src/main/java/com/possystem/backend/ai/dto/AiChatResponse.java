package com.possystem.backend.ai.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiChatResponse {
    String conversationId;
    String messageId;
    String reply;
    List<String> executedTools;
    int promptTokens;
    int completionTokens;
    int totalTokens;
    BigDecimal costEstimateUsd;
    long durationMs;
    LocalDateTime createdAt;
}
