package com.possystem.backend.importorder.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ImportOrderDetailRequest {
    @NotBlank(message = "Mã sản phẩm không được để trống")
    String productId;

    @Min(value = 1, message = "Số lượng nhập tối thiểu là 1")
    int quantity;

    @NotNull(message = "Giá nhập không được để trống")
    @DecimalMin(value = "0.0", message = "Giá nhập không được là số âm")
    BigDecimal importPrice;
}
