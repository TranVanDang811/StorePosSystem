package com.possystem.backend.entity;

import com.possystem.backend.common.enums.ProductType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class Product extends AbstractEntity {
    String name;          // Tên: Đường, Sữa, Trà sữa, Bánh kem
    BigDecimal price;     // Giá bán (0 nếu là nguyên liệu)
    BigDecimal cost;      // Giá nhập trung bình
    String unit;          // kg, g, ml, l, cái, ly...
    BigDecimal stock;     // tồn kho
    LocalDate expiryDate; // hạn sử dụng (nếu có)

    @Enumerated(EnumType.STRING)
    ProductType type;     // INGREDIENT, DRINK, CAKE, OTHER

}
