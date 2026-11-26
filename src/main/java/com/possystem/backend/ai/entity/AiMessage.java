package com.possystem.backend.ai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.possystem.backend.common.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "ai_messages", indexes = {
        @Index(name = "idx_ai_msg_conv", columnList = "conversation_id, created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiMessage extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    @JsonIgnore
    AiConversation conversation;

    @Column(nullable = false, length = 32)
    String role; // USER, ASSISTANT, SYSTEM, TOOL

    @Column(nullable = false, columnDefinition = "TEXT")
    String content;

    @Column(name = "tool_calls_json", columnDefinition = "TEXT")
    String toolCallsJson;
}
