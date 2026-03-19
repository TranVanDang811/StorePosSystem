package com.possystem.backend.discount.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
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
    String code;// mã giảm giá
    String description;

    @Column(nullable = false)
    BigDecimal value;

    boolean active;

    LocalDateTime startDate;

    LocalDateTime endDate;

    public boolean isCurrentlyValid() {
        LocalDateTime now = LocalDateTime.now();
        return (now.isAfter(startDate) || now.isEqual(startDate))
                && (now.isBefore(endDate) || now.isEqual(endDate))
                && active;
    }
}
