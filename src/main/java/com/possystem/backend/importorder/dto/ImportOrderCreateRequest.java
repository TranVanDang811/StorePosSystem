package com.possystem.backend.importorder.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ImportOrderCreateRequest {
    @NotBlank(message = "Nhà cung cấp không được để trống")
    String supplierId;

    String note;

    @NotEmpty(message = "Chi tiết đơn nhập hàng không được để trống")
    @Valid
    List<ImportOrderDetailRequest> importDetails;
}
