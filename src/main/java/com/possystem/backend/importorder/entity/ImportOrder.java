package com.possystem.backend.importorder.entity;

import com.possystem.backend.common.enums.ConfirmStatus;
import com.possystem.backend.common.enums.ImportStatus;
import com.possystem.backend.supplier.entity.Supplier;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

    int totalQuantity;

    BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    ImportStatus status;

    @Enumerated(EnumType.STRING)
    ConfirmStatus confirmStatus;

    @OneToMany(mappedBy = "importOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    List<ImportOrderDetail> importDetails = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Transient
    public int getTotalReceivedQuantity() {
        if (importDetails == null) return 0;
        return importDetails.stream()
                .mapToInt(ImportOrderDetail::getReceivedQuantity)
                .sum();
    }

    @Transient
    public boolean isOverdue() {
        if (importDate == null) return false;
        return importDate.plusDays(3).isBefore(LocalDateTime.now());
    }

    @Transient
    public long getDaysSinceOrder() {
        if (importDate == null) return 0;
        return ChronoUnit.DAYS.between(importDate, LocalDateTime.now());
    }
}
