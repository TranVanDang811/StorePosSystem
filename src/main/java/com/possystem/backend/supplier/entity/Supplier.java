package com.possystem.backend.supplier.entity;

import com.possystem.backend.entity.AbstractEntity;
import jakarta.persistence.Entity;
import lombok.*;
import lombok.experimental.FieldDefaults;

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
}
