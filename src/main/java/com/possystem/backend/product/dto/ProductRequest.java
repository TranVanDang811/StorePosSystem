package com.possystem.backend.product.dto;

import com.possystem.backend.common.enums.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
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
public class ProductRequest {
    String productCode;

    @NotBlank(message = "Tên sản phẩm không được để trống")
    String name;

    @NotNull(message = "Giá bán không được để trống")
    @DecimalMin(value = "0.0", message = "Giá bán không được nhỏ hơn 0")
    BigDecimal price;

    @NotNull(message = "Giá vốn không được để trống")
    @DecimalMin(value = "0.0", message = "Giá vốn không được nhỏ hơn 0")
    BigDecimal cost;

    String unit;

    @DecimalMin(value = "0.0", message = "Tồn kho không được nhỏ hơn 0")
    BigDecimal stock;

    ProductStatus status;
    String categoryName;
    String supplierName;
    String imageUrl;
}
