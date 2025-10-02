package com.possystem.backend.importorder.entity;

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
public class ImportOrderDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    int quantity;

    BigDecimal importPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_order_id")
    ImportOrder importOrder;

    @ManyToOne
    @JoinColumn(name = "product_id")
    Product product;
}
