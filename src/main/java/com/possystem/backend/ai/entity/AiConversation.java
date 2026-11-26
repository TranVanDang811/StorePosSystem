package com.possystem.backend.ai.entity;

import com.possystem.backend.common.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ai_conversations", indexes = {
        @Index(name = "idx_ai_conv_user", columnList = "user_id, created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiConversation extends AbstractEntity {

    @Column(name = "user_id", nullable = false, length = 36)
    String userId;

    @Column(nullable = false, length = 255)
    String title;

    @Builder.Default
    @Column(name = "is_archived", nullable = false)
    boolean archived = false;

    @Builder.Default
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    List<AiMessage> messages = new ArrayList<>();
}
