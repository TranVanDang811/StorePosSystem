package com.possystem.backend.supplier.entity;

import com.possystem.backend.common.entity.AbstractEntity;
import com.possystem.backend.product.entity.Product;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class Supplier extends AbstractEntity {
    String name;

    String phone;

    String email;

    String address;
    @OneToMany(mappedBy = "supplier", cascade = CascadeType.ALL, orphanRemoval = true)
    Set<Product> products = new HashSet<>();
}
