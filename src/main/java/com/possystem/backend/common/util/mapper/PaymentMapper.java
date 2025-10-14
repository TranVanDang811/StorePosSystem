package com.possystem.backend.common.util.mapper;

import com.possystem.backend.payment.dto.PaymentResponse;
import com.possystem.backend.payment.dto.PaymentRequest;
import com.possystem.backend.payment.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    Payment toPayment(PaymentRequest request);

    @Mapping(target = "orderId", source = "order.id")
    PaymentResponse toPaymentResponse(Payment role);
}
