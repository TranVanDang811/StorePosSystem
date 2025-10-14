package com.possystem.backend.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.possystem.backend.common.enums.OrderStatus;
import com.possystem.backend.common.enums.PaymentMethod;
import com.possystem.backend.common.enums.PaymentStatus;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.util.mapper.PaymentMapper;
import com.possystem.backend.order.entity.Orders;
import com.possystem.backend.order.repository.OrderRepository;
import com.possystem.backend.payment.dto.PaymentRequest;
import com.possystem.backend.payment.dto.PaymentResponse;
import com.possystem.backend.payment.dto.RefundRecord;
import com.possystem.backend.payment.dto.RefundRequest;
import com.possystem.backend.payment.entity.Payment;
import com.possystem.backend.payment.repository.PaymentRepository;
import com.possystem.backend.payment.service.PaymentService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentServiceImpl implements PaymentService {
    PaymentRepository paymentRepository;
    OrderRepository orderRepository;
    PaymentMapper paymentMapper;
    final ObjectMapper objectMapper;
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {
        Orders order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (paymentRepository.findByOrder_Id(order.getId()) != null) {
            throw new AppException(ErrorCode.PAYMENT_ALREADY_EXISTS);
        }

        BigDecimal totalAmount = order.getFinalAmount();
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.INVALID_ORDER_AMOUNT);
        }

        Payment payment = Payment.builder()
                .order(order)
                .amount(totalAmount)
                .method(request.getMethod())
                .status(PaymentStatus.SUCCESS)
                .paymentDate(LocalDateTime.now())
                .transactionId("TXN-" + System.currentTimeMillis())
                .note(request.getNote())
                .build();

        paymentRepository.save(payment);

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);
        return PaymentResponse.from(payment);
    }

    public PaymentResponse getByOrderId(String orderId) {
        Payment payment = paymentRepository.findByOrder_Id(orderId);
        if (payment == null) {
            throw new AppException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return PaymentResponse.from(payment);
    }

    public Page<PaymentResponse> getAllPayments(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return paymentRepository.findAll(pageable)
                .map(paymentMapper::toPaymentResponse);
    }


    //--------

    @Transactional
    public PaymentResponse refundPayment(String paymentId, RefundRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getMethod() != PaymentMethod.CASH) {
            throw new AppException(ErrorCode.PAYMENT_NOT_REFUNDABLE);
        }


        if (payment.getStatus() != PaymentStatus.SUCCESS && payment.getStatus() != PaymentStatus.REFUNDED) {
            throw new AppException(ErrorCode.PAYMENT_NOT_REFUNDABLE);
        }

        BigDecimal newTotalRefunded = payment.getTotalRefunded() == null
                ? request.getRefundAmount()
                : payment.getTotalRefunded().add(request.getRefundAmount());

        if (newTotalRefunded.compareTo(payment.getAmount()) > 0) {
            throw new AppException(ErrorCode.INVALID_REFUND_AMOUNT);
        }


        List<RefundRecord> refundRecords = new ArrayList<>();
        if (payment.getRefundHistoryJson() != null) {
            try {
                refundRecords = Arrays.asList(objectMapper.readValue(payment.getRefundHistoryJson(), RefundRecord[].class));
                refundRecords = new ArrayList<>(refundRecords);
            } catch (Exception e) {
                refundRecords = new ArrayList<>();
            }
        }


        refundRecords.add(RefundRecord.builder()
                .amount(request.getRefundAmount())
                .reason(request.getReason())
                .date(LocalDateTime.now())
                .build());

        payment.setTotalRefunded(newTotalRefunded);
        payment.setRefundHistoryJson(writeAsJson(refundRecords));

        if (newTotalRefunded.compareTo(payment.getAmount()) == 0) {
            payment.setStatus(PaymentStatus.REFUNDED);
        }
        paymentRepository.save(payment);
        return PaymentResponse.from(payment);
    }

    private String writeAsJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    public List<RefundRecord> getRefundHistory(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getRefundHistoryJson() == null) {
            return List.of();
        }

        try {
            return Arrays.asList(objectMapper.readValue(payment.getRefundHistoryJson(), RefundRecord[].class));
        } catch (Exception e) {
            throw new AppException(ErrorCode.JSON_PARSE_ERROR);
        }
    }
}
