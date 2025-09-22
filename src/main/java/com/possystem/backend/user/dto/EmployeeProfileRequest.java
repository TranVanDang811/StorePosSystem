package com.possystem.backend.user.dto;

import com.possystem.backend.common.validator.DowConstraint;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmployeeProfileRequest {
    String position;   // chức vụ
    double salary;     // lương cơ bản
    @DowConstraint(min = 16, message = "INVALID_DOW")
    LocalDate hireDate; // ngày vào làm
    String shift;      // ca làm việc
}
