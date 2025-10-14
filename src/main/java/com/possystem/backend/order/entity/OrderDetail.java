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

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    Orders order;  // Đơn hàng chứa sản phẩm này

    @ManyToOne
    Product product;

    @Column(nullable = false)
    Integer quantity;  // Số lượng sản phẩm mua

    @Column(nullable = false)
    BigDecimal totalPrice;  // Tổng tiền cho sản phẩm (productPrice * quantity)
}
