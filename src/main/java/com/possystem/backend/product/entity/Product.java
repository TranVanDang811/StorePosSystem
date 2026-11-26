package com.possystem.backend.product.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.possystem.backend.category.entity.Category;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.common.entity.AbstractEntity;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.supplier.entity.Supplier;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.math.RoundingMode;


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

    @Version
    @Builder.Default
    Long version = 0L;

    @Enumerated(EnumType.STRING)
    ProductStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "supplier_id", nullable = false)
    Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "category_id", nullable = false)
    Category category;  // INGREDIENT, DRINK, CAKE, OTHER

    /**
     * Trừ tồn kho và tự động đổi trạng thái sang OUT_OF_STOCK khi hết hàng.
     */
    public void deductStock(int quantity) {
        if (this.stock < quantity) {
            throw new AppException(ErrorCode.PRODUCT_OUT_OF_STOCK);
        }
        this.stock -= quantity;
        if (this.stock == 0 && this.status != ProductStatus.DISCONTINUED) {
            this.status = ProductStatus.OUT_OF_STOCK;
        }
    }

    /**
     * Cộng thêm tồn kho và kích hoạt lại sản phẩm nếu đang OUT_OF_STOCK.
     */
    public void addStock(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Số lượng nhập thêm không được âm");
        }
        this.stock += quantity;
        if (this.stock > 0 && this.status == ProductStatus.OUT_OF_STOCK) {
            this.status = ProductStatus.ACTIVE;
        }
    }

    /**
     * Cập nhật giá vốn bình quân gia quyền (Weighted Moving Average Cost) khi nhập hàng mới.
     * Công thức: ((Tồn cũ * Giá vốn cũ) + (Nhập mới * Giá nhập mới)) / Tổng tồn mới
     */
    public void updateMovingAverageCost(int newQty, BigDecimal newPrice) {
        if (newQty <= 0) return;
        int oldStock = Math.max(0, this.stock);
        int totalNewStock = oldStock + newQty;
        BigDecimal currentCost = this.cost != null ? this.cost : BigDecimal.ZERO;
        BigDecimal importPrice = newPrice != null ? newPrice : BigDecimal.ZERO;

        if (totalNewStock > 0) {
            BigDecimal oldTotalValue = currentCost.multiply(BigDecimal.valueOf(oldStock));
            BigDecimal newImportValue = importPrice.multiply(BigDecimal.valueOf(newQty));
            this.cost = oldTotalValue.add(newImportValue)
                    .divide(BigDecimal.valueOf(totalNewStock), 2, RoundingMode.HALF_UP);
        } else {
            this.cost = importPrice;
        }
        this.addStock(newQty);
    }
}
