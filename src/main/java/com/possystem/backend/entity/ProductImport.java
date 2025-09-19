package com.possystem.backend.entity;

import com.possystem.backend.common.enums.ImportStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class ProductImport {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    LocalDateTime importDate;

    LocalDateTime importUpdateDate;

    String note;

    @Enumerated(EnumType.STRING)
    ImportStatus status;


    @ManyToOne
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;
}
