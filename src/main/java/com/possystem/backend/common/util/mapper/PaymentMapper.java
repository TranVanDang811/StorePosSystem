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
    @Mapping(target = "userPhone", expression = "java(payment.getUser() != null ? payment.getUser().getPhone() : null)")
    @Mapping(target = "fullName", expression = "java(payment.getUser() != null ? payment.getUser().getFullName() : \"Khách vãng lai\")")

    @Mapping(target = "pointDiscount", source = "pointDiscount")

    @Mapping(target = "finalAmount",
            expression = "java(payment.getOrder().getFinalAmount().subtract(payment.getPointDiscount() != null ? payment.getPointDiscount() : java.math.BigDecimal.ZERO))")

    PaymentResponse toPaymentResponse(Payment payment);
}
