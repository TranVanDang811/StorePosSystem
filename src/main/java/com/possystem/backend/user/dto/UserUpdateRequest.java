package com.possystem.backend.user.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserUpdateRequest {
    String fullName;
    String email;
    String phone;
    CustomerProfileRequest customerProfile;
    EmployeeProfileRequest employeeProfile;
}
