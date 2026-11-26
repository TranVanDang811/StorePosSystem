package com.possystem.backend.notification.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RealtimeEventMessage<T> {
    String eventType;
    String channel;
    String message;
    @Builder.Default
    LocalDateTime timestamp = LocalDateTime.now();
    T payload;
}
