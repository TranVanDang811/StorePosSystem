package com.possystem.backend.importorder.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ImportOrderDetailResponse {
    String id;

    String productId;
    String productName;

    int quantity;
    int receivedQuantity;
    int confirmedQuantity;

    BigDecimal importPrice;
    BigDecimal lineTotal;
}
