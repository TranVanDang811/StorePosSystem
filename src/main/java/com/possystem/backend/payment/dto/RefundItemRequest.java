package com.possystem.backend.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefundItemRequest {
    @NotBlank(message = "Mã sản phẩm hoàn trả không được để trống")
    private String productId;

    @Min(value = 1, message = "Số lượng hoàn trả tối thiểu là 1")
    private int quantity;
}
