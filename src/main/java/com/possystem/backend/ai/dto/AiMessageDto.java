package com.possystem.backend.ai.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiMessageDto {
    String id;
    String conversationId;
    String role;
    String content;
    String toolCallsJson;
    LocalDateTime createdAt;
}
