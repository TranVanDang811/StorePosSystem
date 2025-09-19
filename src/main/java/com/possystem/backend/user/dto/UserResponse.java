package com.possystem.backend.user.dto;

import com.possystem.backend.common.enums.UserStatus;

import com.possystem.backend.role.dto.RoleResponse;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    String id;
    String username;
    String fullName;
    String email;
    String phone;
    UserStatus status;
    Set<RoleResponse> roles;
}
