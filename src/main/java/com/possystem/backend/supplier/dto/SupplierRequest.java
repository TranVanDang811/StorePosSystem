package com.possystem.backend.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SupplierRequest {
    @NotBlank(message = "Tên nhà cung cấp không được để trống")
    String name;

    String phone;

    @Email(message = "Email nhà cung cấp không đúng định dạng")
    String email;

    String address;
}
