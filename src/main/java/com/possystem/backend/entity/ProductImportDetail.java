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
public class ProductImportDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @ManyToOne
    @JoinColumn(name = "import_id")
    ProductImport productImport;

    @ManyToOne
    @JoinColumn(name = "product_id")
    Product product;

    int quantity;
    BigDecimal unitPrice;
    BigDecimal totalPrice;
}
