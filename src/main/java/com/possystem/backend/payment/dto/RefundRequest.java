package com.possystem.backend.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefundRequest {
    @NotEmpty(message = "Danh sách trả hàng không được để trống")
    @Valid
    List<RefundItemRequest> items;

    String reason;
}
