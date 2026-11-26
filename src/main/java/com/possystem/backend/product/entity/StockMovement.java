package com.possystem.backend.product.entity;

import com.possystem.backend.common.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "stock_movement", indexes = {
        @Index(name = "idx_stock_movement_prod_time", columnList = "product_id, created_at"),
        @Index(name = "idx_stock_movement_ref", columnList = "reference_id")
})
public class StockMovement extends AbstractEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    Product product;

    @Column(name = "movement_type", nullable = false, length = 50)
    String movementType; // SALE, IMPORT, REFUND, ADJUSTMENT, INITIAL

    @Column(name = "reference_id", length = 100)
    String referenceId; // order_id, import_order_id, payment_id

    @Column(name = "quantity_before", nullable = false)
    int quantityBefore;

    @Column(name = "quantity_change", nullable = false)
    int quantityChange;

    @Column(name = "quantity_after", nullable = false)
    int quantityAfter;

    @Column(length = 255)
    String note;

    @Column(name = "created_by", length = 36)
    String createdBy;
}
