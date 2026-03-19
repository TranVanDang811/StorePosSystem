package com.possystem.backend.payment.service;


import com.possystem.backend.payment.dto.PaymentRequest;
import com.possystem.backend.payment.dto.PaymentResponse;
import com.possystem.backend.payment.dto.RefundRecord;
import com.possystem.backend.payment.dto.RefundRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface PaymentService {
    PaymentResponse createPayment(PaymentRequest request);
    PaymentResponse getByOrderId(String orderId);
    Page<PaymentResponse> getAllPayments(int page, int size);
    PaymentResponse refundPayment(String paymentId, RefundRequest request);
    List<RefundRecord> getRefundHistory(String paymentId);
    PaymentResponse getPaymentById(String paymentId);
    ResponseEntity<ByteArrayResource> exportInvoiceByPayment(String paymentId);
}
