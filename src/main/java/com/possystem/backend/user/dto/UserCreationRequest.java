package com.possystem.backend.user.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.possystem.backend.common.enums.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserCreationRequest {
    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(min = 3, message = "Tên đăng nhập tối thiểu 3 ký tự")
    String username;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, message = "Mật khẩu tối thiểu 6 ký tự")
    String password;

    String fullName;
    String email;
    String phone;
    UserStatus status;
    String roles;
    CustomerProfileRequest customerProfile;
    EmployeeProfileRequest employeeProfile;

    @JsonSetter("roles")
    public void setRoles(Object rolesObj) {
        if (rolesObj instanceof List<?> list && !list.isEmpty()) {
            this.roles = String.valueOf(list.get(0));
        } else if (rolesObj != null) {
            this.roles = String.valueOf(rolesObj);
        }
    }
}
