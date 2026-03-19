package com.possystem.backend.discount.dto;


import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DiscountResponse {
    String id;
    String name;
    BigDecimal value;
    LocalDateTime startDate;
    LocalDateTime endDate;
    boolean active;
    String code;
}
