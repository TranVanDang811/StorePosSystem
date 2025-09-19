package com.possystem.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class Discount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    String name;

    double discountRate; // tỷ lệ giảm giá (ví dụ 10% -> 0.1)

    LocalDateTime startDate;

    LocalDateTime endDate;

    boolean active;


    String code; // mã giảm giá

    public boolean isCurrentlyValid() {
        LocalDateTime now = LocalDateTime.now();
        return (now.isAfter(startDate) || now.isEqual(startDate))
                && (now.isBefore(endDate) || now.isEqual(endDate))
                && active;
    }
}
