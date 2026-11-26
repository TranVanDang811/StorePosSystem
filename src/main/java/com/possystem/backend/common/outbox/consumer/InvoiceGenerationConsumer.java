package com.possystem.backend.common.outbox.consumer;

import com.possystem.backend.common.enums.PaymentMethod;
import com.possystem.backend.common.enums.PaymentStatus;
import com.possystem.backend.common.outbox.entity.InvoiceRecord;
import com.possystem.backend.common.outbox.event.DomainEventEnvelope;
import com.possystem.backend.common.outbox.event.PosEventType;
import com.possystem.backend.common.outbox.repository.InvoiceRecordRepository;
import com.possystem.backend.common.util.mapper.OrderMapper;
import com.possystem.backend.common.util.mapper.PaymentMapper;
import com.possystem.backend.order.dto.OrderResponse;
import com.possystem.backend.order.entity.Orders;
import com.possystem.backend.payment.dto.PaymentResponse;
import com.possystem.backend.payment.entity.Payment;
import com.possystem.backend.payment.repository.PaymentRepository;
import com.possystem.backend.payment.util.ThermalReceiptRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generates and archives durable HTML invoice records asynchronously after payment commit.
 * Checkout remains fast and unaffected by receipt rendering/archival overhead.
 */
@Slf4j
@Order(3)
@Component
@RequiredArgsConstructor
public class InvoiceGenerationConsumer implements PosEventConsumer {

    private final InvoiceRecordRepository invoiceRecordRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;

    @Override
    public String consumerName() {
        return "InvoiceGenerationConsumer";
    }

    @Override
    public boolean supports(String eventType) {
        return PosEventType.PAYMENT_COMPLETED.equals(eventType)
                || PosEventType.LEGACY_PAYMENT_SUCCESS.equals(eventType);
    }

    @Override
    public void consume(DomainEventEnvelope envelope) {
        Map<String, Object> payload = envelope.getPayload() != null ? envelope.getPayload() : Map.of();
        String paymentId = payload.get("paymentId") != null
                ? String.valueOf(payload.get("paymentId"))
                : (payload.get("id") != null ? String.valueOf(payload.get("id")) : envelope.getAggregateId());

        if (invoiceRecordRepository.existsByPaymentId(paymentId)) {
            return;
        }

        Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
        PaymentResponse paymentResponse;
        OrderResponse orderResponse;
        String orderId;
        String orderCode;

        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            Orders order = payment.getOrder();
            paymentResponse = paymentMapper.toPaymentResponse(payment);
            orderResponse = orderMapper.toResponse(order);
            if (orderResponse.getOrderDetails() == null) {
                orderResponse.setOrderDetails(List.of());
            }
            orderId = order != null ? order.getId() : paymentResponse.getOrderId();
            orderCode = order != null ? order.getOrderCode() : paymentId;
        } else {
            orderId = payload.get("orderId") != null ? String.valueOf(payload.get("orderId")) : paymentId;
            orderCode = payload.get("orderCode") != null ? String.valueOf(payload.get("orderCode")) : orderId;
            BigDecimal amount = toBigDecimal(payload.get("amount"));
            paymentResponse = PaymentResponse.builder()
                    .id(paymentId)
                    .orderId(orderId)
                    .amount(amount)
                    .finalAmount(amount)
                    .method(parseMethod(payload.get("method")))
                    .status(PaymentStatus.SUCCESS)
                    .paymentDate(LocalDateTime.now())
                    .earnedPoints(0)
                    .usedPoints(0)
                    .remainingPoints(0)
                    .build();
            orderResponse = OrderResponse.builder()
                    .id(orderId)
                    .orderCode(orderCode)
                    .totalPrice(amount)
                    .finalAmount(amount)
                    .discountAmount(BigDecimal.ZERO)
                    .orderDetails(List.of())
                    .build();
        }

        String renderedHtml = ThermalReceiptRenderer.render(paymentResponse, orderResponse, "SYSTEM_ASYNC");
        String invoiceNum = "INV-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + paymentId.substring(0, Math.min(8, paymentId.length())).toUpperCase();

        InvoiceRecord record = InvoiceRecord.builder()
                .invoiceNumber(invoiceNum)
                .orderId(orderId != null ? orderId : paymentId)
                .orderCode(orderCode)
                .paymentId(paymentId)
                .totalAmount(paymentResponse.getAmount() != null ? paymentResponse.getAmount() : BigDecimal.ZERO)
                .paymentMethod(paymentResponse.getMethod() != null ? paymentResponse.getMethod().name() : "OTHER")
                .status("GENERATED")
                .renderedHtml(renderedHtml)
                .generatedAt(LocalDateTime.now())
                .build();

        invoiceRecordRepository.save(record);
        log.info("Async invoice generated: {} for payment {}", invoiceNum, paymentId);
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(val));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private PaymentMethod parseMethod(Object val) {
        if (val == null) return PaymentMethod.CASH;
        try {
            return PaymentMethod.valueOf(String.valueOf(val).toUpperCase());
        } catch (Exception e) {
            return PaymentMethod.OTHER;
        }
    }
}
