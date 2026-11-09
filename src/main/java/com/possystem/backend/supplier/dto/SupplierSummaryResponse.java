package com.possystem.backend.supplier.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SupplierSummaryResponse {
    long totalSuppliers;
    long activeSuppliers;
    long idleSuppliers;
    long totalProductsSupplied;
    long totalImportOrders;
}

