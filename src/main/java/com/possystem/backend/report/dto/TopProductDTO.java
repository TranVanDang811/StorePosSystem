package com.possystem.backend.report.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TopProductDTO {
    String productName;
    Integer quantity;
    BigDecimal revenue;
}
