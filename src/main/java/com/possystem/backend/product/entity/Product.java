package com.possystem.backend.product.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.possystem.backend.category.entity.Category;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.common.enums.ProductType;
import com.possystem.backend.entity.AbstractEntity;
import jakarta.persistence.*;
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
    String imageUrl;

    @Enumerated(EnumType.STRING)
    ProductStatus status;

    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "category_id", nullable = false)
    Category category;  // INGREDIENT, DRINK, CAKE, OTHER

}
