package com.possystem.backend.importorder.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ImportOrderCreateRequest {
    String supplierId;
    String note;
    List<ImportOrderDetailRequest> importDetails;
}
