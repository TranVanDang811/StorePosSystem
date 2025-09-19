package com.possystem.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.YearMonth;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class MonthlyReport {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private YearMonth month;
    private BigDecimal totalRevenue;
    private Integer totalOrders;
    private Integer totalItemsSold;
}
