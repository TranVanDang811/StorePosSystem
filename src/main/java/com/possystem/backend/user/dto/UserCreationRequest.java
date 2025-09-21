package com.possystem.backend.user.dto;


import com.possystem.backend.common.enums.UserStatus;
import com.possystem.backend.user.entity.CustomerProfile;
import com.possystem.backend.user.entity.EmployeeProfile;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE) // gan lop
public class UserCreationRequest {

    @Size(min = 3, message = "USERNAME_INVALID")
    String username;
    @Size(min = 8, message = "PASSWORD_INVALID")
    String password;
    String fullName;
    String email;
    String phone;
    UserStatus status;
    String roles;
    CustomerProfile customerProfile;
    EmployeeProfile employeeProfile;
}
