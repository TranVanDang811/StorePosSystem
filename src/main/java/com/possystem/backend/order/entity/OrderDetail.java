package com.possystem.backend.order.entity;

import com.possystem.backend.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class OrderDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    Orders order;  // Đơn hàng chứa sản phẩm này

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    Product product;


    @Column(nullable = false)
    Integer quantity;  // Số lượng sản phẩm mua

    @Builder.Default
    @Column(nullable = false)
    int refundedQuantity = 0;

    BigDecimal price;

    @Column(nullable = false)
    BigDecimal totalPrice;  // Tổng tiền cho sản phẩm (productPrice * quantity)
}
