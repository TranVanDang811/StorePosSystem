package com.possystem.backend.payment.controller;

import com.possystem.backend.common.response.ApiResponse;
import com.possystem.backend.payment.dto.PaymentRequest;
import com.possystem.backend.payment.dto.PaymentResponse;
import com.possystem.backend.payment.dto.RefundRecord;
import com.possystem.backend.payment.dto.RefundRequest;
import com.possystem.backend.payment.entity.Payment;
import com.possystem.backend.payment.service.PaymentService;
import com.possystem.backend.product.dto.ProductResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentController {
    PaymentService paymentService;

    @PostMapping
    public ApiResponse<PaymentResponse> createPayment(@RequestBody PaymentRequest request) {
        return ApiResponse.<PaymentResponse>builder()
                .result(paymentService.createPayment(request))
                .build();
    }

    @GetMapping("/order/{orderId}")
    public ApiResponse<PaymentResponse> getByOrder(@PathVariable String orderId) {
        return ApiResponse.<PaymentResponse>builder()
                .result(paymentService.getByOrderId(orderId))
                .build();
    }

    @GetMapping
    ApiResponse<Page<PaymentResponse>> getAllPayments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size) {
        return ApiResponse.<Page<PaymentResponse>>builder()
                .result(paymentService.getAllPayments(page - 1, size))
                .build();
    }

    @PostMapping("/{paymentId}/refund")
    public ApiResponse<PaymentResponse> refundPayment(
            @PathVariable String paymentId,
            @RequestBody RefundRequest request
    ) {

        PaymentResponse response =paymentService.refundPayment(paymentId, request);
        return ApiResponse.<PaymentResponse>builder()
                .result(response)
                .build();
    }

    @GetMapping("/{paymentId}/refund-history")
    public ApiResponse<List<RefundRecord>> getRefundHistory(@PathVariable String paymentId) {
        return ApiResponse.<List<RefundRecord>>builder()
                .result(paymentService.getRefundHistory(paymentId))
                .build();
    }

}
