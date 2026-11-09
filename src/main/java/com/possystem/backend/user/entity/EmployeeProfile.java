package com.possystem.backend.user.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmployeeProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    String position;   // chức vụ

    @Column(precision = 15, scale = 2)
    BigDecimal salary; // lương cơ bản

    LocalDate hireDate; // ngày vào làm
    String shift;      // ca làm việc

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonBackReference
    User user;
}
