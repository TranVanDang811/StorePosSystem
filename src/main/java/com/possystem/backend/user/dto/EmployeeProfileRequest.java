package com.possystem.backend.user.dto;

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
    LocalDate hireDate; // ngày vào làm
    String shift;      // ca làm việc
}
