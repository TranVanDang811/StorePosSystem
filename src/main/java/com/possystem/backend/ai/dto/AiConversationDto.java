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
public class AiConversationDto {
    String id;
    String title;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    int messageCount;
}
