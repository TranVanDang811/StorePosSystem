package com.possystem.backend.user.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class EmployeeProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    String position;   // chức vụ
    double salary;     // lương cơ bản
    LocalDateTime hireDate; // ngày vào làm
    String shift;      // ca làm việc

    @OneToOne
    @JoinColumn(name = "user_id")
    @JsonBackReference
    User user;
}
