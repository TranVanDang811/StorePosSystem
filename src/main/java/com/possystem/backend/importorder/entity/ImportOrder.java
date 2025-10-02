package com.possystem.backend.importorder.entity;

import com.possystem.backend.common.enums.ImportStatus;
import com.possystem.backend.supplier.entity.Supplier;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class ImportOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    LocalDateTime importDate;

    LocalDateTime importUpdateDate;

    String note;

    BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    ImportStatus status;

    @OneToMany(mappedBy = "importOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    List<ImportOrderDetail> importDetails = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;
}
