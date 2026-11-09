package com.possystem.backend.payment.controller;

import com.possystem.backend.common.response.ApiResponse;
import com.possystem.backend.order.service.OrderService;
import com.possystem.backend.payment.dto.PaymentRequest;
import com.possystem.backend.payment.dto.PaymentResponse;
import com.possystem.backend.payment.dto.PaymentSummaryResponse;
import com.possystem.backend.payment.dto.RefundRecord;
import com.possystem.backend.payment.dto.RefundRequest;
import com.possystem.backend.payment.service.PaymentService;
import com.possystem.backend.user.dto.CustomerHistoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
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
        public ApiResponse<PaymentResponse> createPayment(@RequestBody @Valid PaymentRequest request) {
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
        ApiResponse<Page<PaymentResponse>> getPayments(
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "10") int size,
                        @RequestParam(required = false) String fromDate,
                        @RequestParam(required = false) String toDate,
                        @RequestParam(required = false) String status,
                        @RequestParam(required = false) String method,
                        @RequestParam(required = false) String keyword) {
                return ApiResponse.<Page<PaymentResponse>>builder()
                                .result(paymentService.getPayments(
                                                page - 1,
                                                size,
                                                fromDate,
                                                toDate,
                                                status,
                                                method,
                                                keyword))
                                .build();
        }

        @Operation(summary = "Get payment summary stats", description = "Retrieve aggregated financial stats for payments matching filter criteria.")
        @GetMapping("/summary")
        public ApiResponse<PaymentSummaryResponse> getPaymentSummary(
                        @RequestParam(required = false) String fromDate,
                        @RequestParam(required = false) String toDate,
                        @RequestParam(required = false) String status,
                        @RequestParam(required = false) String method,
                        @RequestParam(required = false) String keyword) {
                return ApiResponse.<PaymentSummaryResponse>builder()
                                .result(paymentService.getPaymentSummary(fromDate, toDate, status, method, keyword))
                                .build();
        }

        @Operation(summary = "Refund a payment", description = "Initiate a refund for a specific payment. Only applicable for cash transactions.")
        @PostMapping("/refund-items/{paymentId}")
        public ApiResponse<PaymentResponse> refundItems(
                        @PathVariable String paymentId,
                        @RequestBody @Valid RefundRequest request) {
                return ApiResponse.<PaymentResponse>builder()
                                .result(paymentService.refundItems(paymentId, request))
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
        @GetMapping("/invoice-html/{paymentId}")
        public ResponseEntity<String> exportInvoiceHtml(@PathVariable String paymentId) {
                return paymentService.exportInvoiceHtml(paymentId);
        }

        @Operation(summary = "Get customer purchase history", description = "Retrieve paginated purchase history of a customer.")
        @GetMapping("/customer/{userId}")
        public ApiResponse<Page<PaymentResponse>> getPaymentsByCustomer(
                        @PathVariable String userId,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "10") int size) {
                return ApiResponse.<Page<PaymentResponse>>builder()
                                .result(paymentService.getPaymentsByUserId(userId, page, size))
                                .build();
        }

        @Operation(summary = "Get customer spending stats", description = "Retrieve aggregate spending statistics of a customer.")
        @GetMapping("/customer/{userId}/stats")
        public ApiResponse<CustomerHistoryResponse> getCustomerStats(@PathVariable String userId) {
                return ApiResponse.<CustomerHistoryResponse>builder()
                                .result(paymentService.getCustomerHistoryStats(userId))
                                .build();
        }
}
