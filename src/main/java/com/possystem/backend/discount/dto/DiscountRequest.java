package com.possystem.backend.discount.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DiscountRequest {
    @NotBlank(message = "Tên chương trình khuyến mãi không được để trống")
    String name;

    @NotNull(message = "Giá trị khuyến mãi không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá trị khuyến mãi phải lớn hơn 0")
    BigDecimal value;

    LocalDateTime startDate;
    LocalDateTime endDate;
    boolean active;

    @NotBlank(message = "Mã khuyến mãi không được để trống")
    String code;
}
