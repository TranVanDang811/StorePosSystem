package com.possystem.backend.entity;

import com.possystem.backend.product.entity.Product;
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
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @ManyToOne
    Order order;

    @ManyToOne
    Product product;

    BigDecimal quantity;
    BigDecimal price;       // giá bán / 1 sp
    BigDecimal totalPrice;  // quantity * price
}
