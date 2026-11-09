package com.possystem.backend.payment.service;

import com.possystem.backend.payment.dto.PaymentRequest;
import com.possystem.backend.payment.dto.PaymentResponse;
import com.possystem.backend.payment.dto.PaymentSummaryResponse;
import com.possystem.backend.payment.dto.RefundRecord;
import com.possystem.backend.payment.dto.RefundRequest;
import com.possystem.backend.user.dto.CustomerHistoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface PaymentService {
    PaymentResponse createPayment(PaymentRequest request);
    PaymentResponse getByOrderId(String orderId);
    Page<PaymentResponse> getPayments(
            int page,
            int size,
            String fromDate,
            String toDate,
            String status,
            String method,
            String keyword
    );
    PaymentResponse refundItems(String paymentId, RefundRequest request);
    List<RefundRecord> getRefundHistory(String paymentId);
    PaymentResponse getPaymentById(String paymentId);
    ResponseEntity<String> exportInvoiceHtml(String paymentId);

    Page<PaymentResponse> getPaymentsByUserId(String userId, int page, int size);
    CustomerHistoryResponse getCustomerHistoryStats(String userId);

    PaymentSummaryResponse getPaymentSummary(
            String fromDate,
            String toDate,
            String status,
            String method,
            String keyword
    );
}
