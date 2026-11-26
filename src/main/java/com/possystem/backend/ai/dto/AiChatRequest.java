package com.possystem.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiChatRequest {

    String conversationId; // Optional: If null, a new conversation is started

    @NotBlank(message = "Nội dung câu hỏi không được để trống")
    String message;
}
