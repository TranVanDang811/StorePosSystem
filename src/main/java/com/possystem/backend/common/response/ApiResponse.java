package com.possystem.backend.common.response;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    @Builder.Default
    int code = 1000; // business code (1000 = OK)

    @Builder.Default
    boolean success = true;

    String message;
    T result;

    @Builder.Default
    LocalDateTime timestamp = LocalDateTime.now();

    // Factory method cho success
    public static <T> ApiResponse<T> success(T result) {
        return ApiResponse.<T>builder()
                .success(true)
                .code(1000)
                .message("Success")
                .result(result)
                .build();
    }

    // Factory method cho error
    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .build();
    }
}
