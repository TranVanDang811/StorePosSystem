package com.possystem.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthenticationRequest {
    @NotBlank(message = "Tên đăng nhập không được để trống")
    String username;

    @NotBlank(message = "Mật khẩu không được để trống")
    String password;
}
