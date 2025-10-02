package com.possystem.backend.product.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductFilterRequest {
    int page;
    int size;

    String keyword;
    String categoryName;
    String productCode;
    String price;
    String status; // ProductStatus

    String sortByPrice;     // ASC / DESC
    String sortByName;      // ASC / DESC
    String sortByCreatedAt; // ASC / DESC
}
