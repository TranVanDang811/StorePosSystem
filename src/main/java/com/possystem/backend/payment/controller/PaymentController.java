package com.possystem.backend.payment.controller;

import com.possystem.backend.common.response.ApiResponse;
import com.possystem.backend.order.service.OrderService;
import com.possystem.backend.payment.dto.PaymentRequest;
import com.possystem.backend.payment.dto.PaymentResponse;
import com.possystem.backend.payment.dto.RefundRecord;
import com.possystem.backend.payment.dto.RefundRequest;
import com.possystem.backend.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Payment Management", description = "Endpoints for handling payments, refunds, and invoices")
public class PaymentController {
    PaymentService paymentService;
    OrderService orderService;

    @Operation(summary = "Create a new payment", description = "Create a payment record for a specific order.")
    @PostMapping
    public ApiResponse<PaymentResponse> createPayment(@RequestBody PaymentRequest request) {
        return ApiResponse.<PaymentResponse>builder()
                .result(paymentService.createPayment(request))
                .build();
    }
    @Operation(summary = "Get payment by order ID", description = "Retrieve payment information associated with a specific order.")
    @GetMapping("/order/{orderId}")
    public ApiResponse<PaymentResponse> getByOrder(@PathVariable String orderId) {
        return ApiResponse.<PaymentResponse>builder()
                .result(paymentService.getByOrderId(orderId))
                .build();
    }

    @Operation(summary = "Get payment by ID", description = "Retrieve payment details by its unique ID.")
    @GetMapping("/{paymentId}")
    public ApiResponse<PaymentResponse> getPaymentById(@PathVariable String paymentId) {
        return ApiResponse.<PaymentResponse>builder()
                .result(paymentService.getPaymentById(paymentId))
                .build();
    }

    @Operation(summary = "Get all payments", description = "Retrieve a paginated list of all payment records.")
    @GetMapping
    ApiResponse<Page<PaymentResponse>> getAllPayments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size) {
        return ApiResponse.<Page<PaymentResponse>>builder()
                .result(paymentService.getAllPayments(page - 1, size))
                .build();
    }

    @Operation(summary = "Refund a payment", description = "Initiate a refund for a specific payment. Only applicable for cash transactions.")
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

    @Operation(summary = "Get refund history", description = "Retrieve all refund transactions associated with a specific payment.")
    @GetMapping("/{paymentId}/refund-history")
    public ApiResponse<List<RefundRecord>> getRefundHistory(@PathVariable String paymentId) {
        return ApiResponse.<List<RefundRecord>>builder()
                .result(paymentService.getRefundHistory(paymentId))
                .build();
    }

    @Operation(summary = "Export invoice by payment ID", description = "Download an invoice (PDF format) for a specific payment.")
    @GetMapping("/invoice/{paymentId}")
    public ResponseEntity<ByteArrayResource> exportInvoice(@PathVariable String paymentId) {
        return paymentService.exportInvoiceByPayment(paymentId);
    }

}
