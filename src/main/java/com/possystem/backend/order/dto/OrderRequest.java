package com.possystem.backend.order.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderRequest {
    String userId;
    List<OrderDetailRequest> orderDetails;
    String discountCode;
    Integer usedPoints;
}
