package com.possystem.backend.payment.controller;

import com.possystem.backend.common.response.ApiResponse;
import com.possystem.backend.payment.dto.WebhookPaymentPayload;
import com.possystem.backend.payment.dto.WebhookResult;
import com.possystem.backend.payment.entity.WebhookEvent;
import com.possystem.backend.payment.service.PaymentWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payments/webhook")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Payment Webhook & Reconciliation Engine", description = "Xử lý IPN/Webhook thanh toán lũy biến (Idempotent) & Đối soát tự động")
public class PaymentWebhookController {

    PaymentWebhookService paymentWebhookService;

    @PostMapping
    @Operation(summary = "Tiếp nhận webhook thanh toán chung có hỗ trợ Idempotency-Key")
    public ResponseEntity<ApiResponse<WebhookResult>> handleGenericWebhook(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKeyHeader,
            @RequestParam(value = "provider", defaultValue = "GENERIC") String provider,
            @RequestBody WebhookPaymentPayload payload
    ) {
        String key = idempotencyKeyHeader != null ? idempotencyKeyHeader : xIdempotencyKeyHeader;
        WebhookResult result = paymentWebhookService.processWebhook(provider, key, payload);

        return ResponseEntity.ok(ApiResponse.<WebhookResult>builder()
                .code(result.isSuccess() ? 200 : 400)
                .message(result.getMessage())
                .result(result)
                .build());
    }

    @PostMapping("/vietqr")
    @Operation(summary = "Webhook tiếp nhận thông báo biến động số dư VietQR / Chuyển khoản ngân hàng")
    public ResponseEntity<ApiResponse<WebhookResult>> handleVietQrWebhook(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody WebhookPaymentPayload payload
    ) {
        payload.setPaymentMethod("BANK_TRANSFER");
        payload.setProvider("VIETQR");
        WebhookResult result = paymentWebhookService.processWebhook("VIETQR", idempotencyKey, payload);

        return ResponseEntity.ok(ApiResponse.<WebhookResult>builder()
                .code(result.isSuccess() ? 200 : 400)
                .message(result.getMessage())
                .result(result)
                .build());
    }

    @PostMapping("/vnpay")
    @Operation(summary = "Webhook IPN tiếp nhận thanh toán VNPay QR / Cổng thẻ nội địa")
    public ResponseEntity<ApiResponse<WebhookResult>> handleVnPayWebhook(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody WebhookPaymentPayload payload
    ) {
        payload.setPaymentMethod("VNPAY");
        payload.setProvider("VNPAY");
        WebhookResult result = paymentWebhookService.processWebhook("VNPAY", idempotencyKey, payload);

        return ResponseEntity.ok(ApiResponse.<WebhookResult>builder()
                .code(result.isSuccess() ? 200 : 400)
                .message(result.getMessage())
                .result(result)
                .build());
    }

    @PostMapping("/momo")
    @Operation(summary = "Webhook IPN tiếp nhận thanh toán Ví MoMo")
    public ResponseEntity<ApiResponse<WebhookResult>> handleMoMoWebhook(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody WebhookPaymentPayload payload
    ) {
        payload.setPaymentMethod("MOMO");
        payload.setProvider("MOMO");
        WebhookResult result = paymentWebhookService.processWebhook("MOMO", idempotencyKey, payload);

        return ResponseEntity.ok(ApiResponse.<WebhookResult>builder()
                .code(result.isSuccess() ? 200 : 400)
                .message(result.getMessage())
                .result(result)
                .build());
    }

    @PostMapping("/reconcile")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    @Operation(summary = "Kích hoạt đối soát tự động các giao dịch treo/chờ thanh toán")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runReconciliation() {
        Map<String, Object> stats = paymentWebhookService.reconcilePendingWebhooks();
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(200)
                .message("Đối soát giao dịch hoàn tất.")
                .result(stats)
                .build());
    }

    @GetMapping("/events")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    @Operation(summary = "Xem lịch sử nhật ký webhook & idempotency log")
    public ResponseEntity<ApiResponse<List<WebhookEvent>>> getRecentEvents(
            @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        List<WebhookEvent> events = paymentWebhookService.getRecentWebhookEvents(limit);
        return ResponseEntity.ok(ApiResponse.<List<WebhookEvent>>builder()
                .code(200)
                .result(events)
                .build());
    }
}
