package com.possystem.backend.user.dto;


import com.possystem.backend.common.enums.UserStatus;
import com.possystem.backend.user.entity.CustomerProfile;
import com.possystem.backend.user.entity.EmployeeProfile;
import lombok.*;
import lombok.experimental.FieldDefaults;



@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE) // gan lop
public class UserCreationRequest {
    String username;
    String password;
    String fullName;
    String email;
    String phone;
    UserStatus status;
    String roles;
    CustomerProfile customerProfile;
    EmployeeProfile employeeProfile;
}
