package com.possystem.backend.product.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.possystem.backend.category.entity.Category;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.common.entity.AbstractEntity;
import com.possystem.backend.supplier.entity.Supplier;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;


@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "product_code"))
public class    Product extends AbstractEntity {
    @Column(name = "product_code", nullable = false)
    String productCode; // Mã sản phẩm
    String name;          // Tên: Đường, Sữa, Trà sữa, Bánh kem
    BigDecimal price;     // Giá bán (0 nếu là nguyên liệu)
    BigDecimal cost;      // Giá nhập trung bình
    String unit;          // kg, g, ml, l, cái, ly...
    int stock;     // tồn kho
    String imageUrl;

    @Enumerated(EnumType.STRING)
    ProductStatus status;

    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "supplier_id", nullable = false)
    Supplier supplier;

    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "category_id", nullable = false)
    Category category;  // INGREDIENT, DRINK, CAKE, OTHER

}
