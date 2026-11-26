package com.possystem.backend.payment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.possystem.backend.common.enums.OrderStatus;
import com.possystem.backend.common.enums.PaymentMethod;
import com.possystem.backend.common.enums.PaymentStatus;
import com.possystem.backend.common.observability.PosBusinessMetrics;
import com.possystem.backend.common.observability.PosTracingHelper;
import com.possystem.backend.common.observability.TraceSanitizer;
import com.possystem.backend.common.outbox.event.PosEventType;
import io.micrometer.tracing.Span;
import com.possystem.backend.common.outbox.service.OutboxPublisher;
import com.possystem.backend.order.entity.OrderDetail;
import com.possystem.backend.order.entity.Orders;
import com.possystem.backend.order.repository.OrderRepository;
import com.possystem.backend.payment.dto.WebhookPaymentPayload;
import com.possystem.backend.payment.dto.WebhookResult;
import com.possystem.backend.payment.entity.Payment;
import com.possystem.backend.payment.entity.WebhookEvent;
import com.possystem.backend.payment.repository.PaymentRepository;
import com.possystem.backend.payment.repository.WebhookEventRepository;
import com.possystem.backend.payment.service.PaymentWebhookService;
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.product.entity.StockMovement;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.product.repository.StockMovementRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentWebhookServiceImpl implements PaymentWebhookService {

    WebhookEventRepository webhookEventRepository;
    OrderRepository orderRepository;
    PaymentRepository paymentRepository;
    ProductRepository productRepository;
    StockMovementRepository stockMovementRepository;
    OutboxPublisher outboxPublisher;
    ObjectMapper objectMapper;
    PosBusinessMetrics businessMetrics;
    PosTracingHelper posTracingHelper;

    @Override
    @Transactional
    public WebhookResult processWebhook(String provider, String idempotencyKey, WebhookPaymentPayload payload) {
        String safeProvider = TraceSanitizer.sanitizeBoundedLabel(provider, "EXTERNAL_WEBHOOK");
        if (posTracingHelper != null) {
            return posTracingHelper.traceSupplier(
                    "pos.external.payment_webhook",
                    Span.Kind.CLIENT,
                    null,
                    Map.of("external.provider", safeProvider, "external.operation", "webhook"),
                    () -> doProcessWebhook(provider, safeProvider, idempotencyKey, payload));
        }
        return doProcessWebhook(provider, safeProvider, idempotencyKey, payload);
    }

    private WebhookResult doProcessWebhook(
            String provider, String safeProvider, String idempotencyKey, WebhookPaymentPayload payload) {
        long startNanos = System.nanoTime();
        String safeKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey
                : generateIdempotencyKey(provider, payload);

        // 1. Kiểm tra tính lũy biến (Idempotency Check)
        Optional<WebhookEvent> existingEventOpt = webhookEventRepository.findByIdempotencyKey(safeKey);
        if (existingEventOpt.isPresent()) {
            WebhookEvent existing = existingEventOpt.get();
            if (existing.getStatus() == WebhookEvent.WebhookStatus.PROCESSED) {
                log.info("Webhook duplicate ignored for key: {}", safeKey);
                return WebhookResult.builder()
                        .success(true)
                        .isDuplicate(true)
                        .idempotencyKey(safeKey)
                        .orderId(existing.getOrderId())
                        .message("Giao dịch trùng lặp đã được xử lý thành công trước đó (Idempotent replay).")
                        .build();
            } else if (existing.getStatus() == WebhookEvent.WebhookStatus.PENDING) {
                return WebhookResult.builder()
                        .success(false)
                        .isDuplicate(true)
                        .idempotencyKey(safeKey)
                        .orderId(existing.getOrderId())
                        .message("Giao dịch đang được xử lý song song bởi tiến trình khác.")
                        .build();
            }
        }

        // 2. Khởi tạo bản ghi webhook event ở trạng thái PENDING
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Không thể tuần tự hóa payload webhook: {}", e.getMessage());
            json = "";
        }
        final String payloadJson = json;

        WebhookEvent event = existingEventOpt.orElseGet(() -> WebhookEvent.builder()
                .idempotencyKey(safeKey)
                .provider(provider.toUpperCase())
                .orderId(payload.getOrderId())
                .amount(payload.getAmount())
                .status(WebhookEvent.WebhookStatus.PENDING)
                .payload(payloadJson)
                .retryCount(0)
                .build());

        event.setStatus(WebhookEvent.WebhookStatus.PENDING);
        webhookEventRepository.save(event);

        try {
            // 3. Tìm đơn hàng tương ứng theo ID hoặc mã đơn hàng (orderCode)
            Orders order = null;
            if (payload.getOrderId() != null && !payload.getOrderId().isBlank()) {
                order = orderRepository.findById(payload.getOrderId())
                        .orElse(orderRepository.findByOrderCode(payload.getOrderId()).orElse(null));
            }

            if (order == null) {
                event.setStatus(WebhookEvent.WebhookStatus.FAILED);
                event.setResponseBody("Không tìm thấy đơn hàng với mã: " + payload.getOrderId());
                webhookEventRepository.save(event);

                if (outboxPublisher != null) {
                    Map<String, Object> failPayload = new LinkedHashMap<>();
                    failPayload.put("orderId", payload.getOrderId());
                    failPayload.put("provider", provider);
                    failPayload.put("reason", "ORDER_NOT_FOUND");
                    outboxPublisher.publish(
                            "PAYMENT",
                            safeKey,
                            PosEventType.PAYMENT_FAILED,
                            "payment:" + safeKey,
                            safeKey,
                            failPayload);
                }
                if (businessMetrics != null) {
                    businessMetrics.recordPaymentFailed(
                            resolvePaymentMethod(provider, payload.getPaymentMethod()), "ORDER_NOT_FOUND", "WEBHOOK");
                    businessMetrics.recordExternalCall("webhook", safeProvider, "FAILED", System.nanoTime() - startNanos);
                }

                return WebhookResult.builder()
                        .success(false)
                        .isDuplicate(false)
                        .idempotencyKey(safeKey)
                        .orderId(payload.getOrderId())
                        .message("Đơn hàng không tồn tại trong hệ thống POS.")
                        .build();
            }

            // 4. Nếu đơn hàng đã PAID
            if (order.getStatus() == OrderStatus.PAID) {
                event.setStatus(WebhookEvent.WebhookStatus.PROCESSED);
                event.setProcessedAt(LocalDateTime.now());
                event.setResponseBody("Đơn hàng đã được thanh toán trước đó.");
                webhookEventRepository.save(event);

                return WebhookResult.builder()
                        .success(true)
                        .isDuplicate(true)
                        .idempotencyKey(safeKey)
                        .orderId(order.getId())
                        .message("Đơn hàng đã được thanh toán hoàn tất.")
                        .build();
            }

            // 5. Xác định phương thức thanh toán
            PaymentMethod method = resolvePaymentMethod(provider, payload.getPaymentMethod());

            BigDecimal paymentAmount = (payload.getAmount() != null && payload.getAmount().compareTo(BigDecimal.ZERO) > 0)
                    ? payload.getAmount()
                    : order.getFinalAmount();

            // 6. Tạo thực thể thanh toán (Payment)
            Payment payment = Payment.builder()
                    .order(order)
                    .amount(paymentAmount)
                    .method(method)
                    .status(PaymentStatus.SUCCESS)
                    .paymentDate(LocalDateTime.now())
                    .transactionId(payload.getTransactionId())
                    .note(payload.getNote() != null ? payload.getNote() : "Thanh toán tự động qua Webhook: " + provider)
                    .cashReceived(paymentAmount)
                    .changeAmount(BigDecimal.ZERO)
                    .pointDiscount(BigDecimal.ZERO)
                    .usedPoints(0)
                    .earnedPoints(0)
                    .build();

            payment = paymentRepository.save(payment);

            // 7. Cập nhật trạng thái đơn hàng & trừ tồn kho + lưu audit kho + phát Outbox events
            order.setStatus(OrderStatus.PAID);
            List<Map<String, Object>> stockEvents = new ArrayList<>();
            if (order.getOrderDetails() != null) {
                for (OrderDetail detail : order.getOrderDetails()) {
                    Product product = detail.getProduct();
                    if (product != null) {
                        int beforeStock = product.getStock();
                        int remainingStock = Math.max(0, beforeStock - detail.getQuantity());
                        product.setStock(remainingStock);
                        productRepository.save(product);

                        if (stockMovementRepository != null) {
                            stockMovementRepository.save(StockMovement.builder()
                                    .product(product)
                                    .movementType("SALE")
                                    .referenceId(order.getId())
                                    .quantityBefore(beforeStock)
                                    .quantityChange(-detail.getQuantity())
                                    .quantityAfter(remainingStock)
                                    .note("Webhook thanh toán " + provider + " - Đơn " + order.getOrderCode())
                                    .build());
                        }

                        Map<String, Object> invPayload = new LinkedHashMap<>();
                        invPayload.put("productId", product.getId());
                        invPayload.put("productCode", product.getProductCode());
                        invPayload.put("productName", product.getName());
                        invPayload.put("movementType", "SALE");
                        invPayload.put("beforeStock", beforeStock);
                        invPayload.put("quantityChange", -detail.getQuantity());
                        invPayload.put("afterStock", remainingStock);
                        invPayload.put("referenceId", order.getId());
                        stockEvents.add(invPayload);
                    }
                }
            }
            orderRepository.save(order);

            // 8. Đánh dấu webhook event hoàn thành (PROCESSED)
            event.setStatus(WebhookEvent.WebhookStatus.PROCESSED);
            event.setProcessedAt(LocalDateTime.now());
            event.setResponseBody("Xử lý thanh toán thành công. PaymentId: " + payment.getId());
            webhookEventRepository.save(event);

            if (outboxPublisher != null) {
                Map<String, Object> payPayload = new LinkedHashMap<>();
                payPayload.put("paymentId", payment.getId());
                payPayload.put("orderId", order.getId());
                payPayload.put("orderCode", order.getOrderCode());
                payPayload.put("amount", payment.getAmount());
                payPayload.put("method", payment.getMethod() != null ? payment.getMethod().name() : null);
                payPayload.put("transactionId", payment.getTransactionId());
                payPayload.put("provider", provider);
                outboxPublisher.publishInCurrentTransaction(
                        "PAYMENT",
                        payment.getId(),
                        PosEventType.PAYMENT_COMPLETED,
                        "order:" + order.getId(),
                        order.getOrderCode(),
                        payPayload);

                Map<String, Object> ordPayload = new LinkedHashMap<>();
                ordPayload.put("orderId", order.getId());
                ordPayload.put("orderCode", order.getOrderCode());
                ordPayload.put("paymentId", payment.getId());
                ordPayload.put("amount", payment.getAmount());
                ordPayload.put("status", OrderStatus.PAID.name());
                outboxPublisher.publishInCurrentTransaction(
                        "ORDER",
                        order.getId(),
                        PosEventType.ORDER_COMPLETED,
                        "order:" + order.getId(),
                        order.getOrderCode(),
                        ordPayload);

                for (Map<String, Object> invPayload : stockEvents) {
                    String pid = String.valueOf(invPayload.get("productId"));
                    outboxPublisher.publishInCurrentTransaction(
                            "INVENTORY",
                            pid,
                            PosEventType.INVENTORY_UPDATED,
                            "product:" + pid,
                            order.getOrderCode(),
                            invPayload);
                }
            }

            log.info("Webhook xử lý thành công đơn hàng {} qua {}. PaymentId: {}",
                    order.getId(), provider, payment.getId());

            if (businessMetrics != null) {
                businessMetrics.recordPaymentCompleted(payment.getMethod(), payment.getAmount(), "WEBHOOK");
                for (Map<String, Object> invPayload : stockEvents) {
                    Object deltaObj = invPayload.get("quantityChange");
                    if (deltaObj instanceof Number n) {
                        businessMetrics.recordInventoryAdjustment("SALE", n.intValue());
                    }
                }
                businessMetrics.recordExternalCall("webhook", safeProvider, "SUCCESS", System.nanoTime() - startNanos);
            }

            return WebhookResult.builder()
                    .success(true)
                    .isDuplicate(false)
                    .idempotencyKey(safeKey)
                    .paymentId(payment.getId())
                    .orderId(order.getId())
                    .message("Xử lý thanh toán webhook thành công.")
                    .build();

        } catch (Exception ex) {
            log.error("Lỗi khi xử lý webhook key: {}", safeKey, ex);
            event.setStatus(WebhookEvent.WebhookStatus.FAILED);
            event.setRetryCount(event.getRetryCount() != null ? event.getRetryCount() + 1 : 1);
            event.setResponseBody("Lỗi xử lý: " + ex.getMessage());
            webhookEventRepository.save(event);

            if (outboxPublisher != null) {
                Map<String, Object> failPayload = new LinkedHashMap<>();
                failPayload.put("orderId", payload.getOrderId());
                failPayload.put("provider", provider);
                failPayload.put("reason", ex.getMessage());
                outboxPublisher.publishFailureInNewTransaction(
                        "PAYMENT",
                        safeKey,
                        PosEventType.PAYMENT_FAILED,
                        "payment:" + safeKey,
                        safeKey,
                        failPayload);
            }
            if (businessMetrics != null) {
                businessMetrics.recordPaymentFailed(
                        resolvePaymentMethod(provider, payload.getPaymentMethod()), ex.getMessage(), "WEBHOOK");
                businessMetrics.recordExternalCall("webhook", safeProvider, "FAILED", System.nanoTime() - startNanos);
            }

            return WebhookResult.builder()
                    .success(false)
                    .isDuplicate(false)
                    .idempotencyKey(safeKey)
                    .message("Lỗi hệ thống khi xử lý webhook: " + ex.getMessage())
                    .build();
        }
    }

    @Override
    @Transactional
    public Map<String, Object> reconcilePendingWebhooks() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);
        List<WebhookEvent> pendingEvents = webhookEventRepository.findAllByStatusAndCreatedAtBefore(
                WebhookEvent.WebhookStatus.PENDING, threshold);

        int scanned = pendingEvents.size();
        int resolved = 0;
        int failed = 0;

        for (WebhookEvent event : pendingEvents) {
            try {
                if (event.getOrderId() != null) {
                    Optional<Orders> orderOpt = orderRepository.findById(event.getOrderId());
                    if (orderOpt.isPresent() && orderOpt.get().getStatus() == OrderStatus.PAID) {
                        event.setStatus(WebhookEvent.WebhookStatus.PROCESSED);
                        event.setProcessedAt(LocalDateTime.now());
                        event.setResponseBody("Đối soát tự động: Đơn hàng đã hoàn tất thanh toán.");
                        webhookEventRepository.save(event);
                        resolved++;
                        continue;
                    }
                }

                // Nếu retry quá 3 lần và vẫn treo -> Chuyển sang FAILED
                if (event.getRetryCount() != null && event.getRetryCount() >= 3) {
                    event.setStatus(WebhookEvent.WebhookStatus.FAILED);
                    event.setResponseBody("Đối soát: Vượt quá số lần thử lại tối đa. Chuyển trạng thái thất bại.");
                    webhookEventRepository.save(event);
                    failed++;
                }
            } catch (Exception e) {
                log.warn("Lỗi đối soát bản ghi {}: {}", event.getId(), e.getMessage());
                failed++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanned", scanned);
        result.put("resolved", resolved);
        result.put("failed", failed);
        result.put("timestamp", LocalDateTime.now());
        return result;
    }

    @Override
    public List<WebhookEvent> getRecentWebhookEvents(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return webhookEventRepository.findAll(
                PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();
    }

    private PaymentMethod resolvePaymentMethod(String provider, String requestedMethod) {
        if (requestedMethod != null && !requestedMethod.isBlank()) {
            try {
                return PaymentMethod.valueOf(requestedMethod.toUpperCase());
            } catch (Exception ignored) {}
        }
        if (provider != null) {
            String p = provider.toUpperCase();
            if (p.contains("VNPAY")) return PaymentMethod.VNPAY;
            if (p.contains("MOMO")) return PaymentMethod.MOMO;
            if (p.contains("ZALOPAY")) return PaymentMethod.ZALOPAY;
            if (p.contains("VIETQR") || p.contains("PAYOS") || p.contains("BANK")) return PaymentMethod.BANK_TRANSFER;
        }
        return PaymentMethod.BANK_TRANSFER;
    }

    private String generateIdempotencyKey(String provider, WebhookPaymentPayload payload) {
        if (payload.getTransactionId() != null && !payload.getTransactionId().isBlank()) {
            return provider.toUpperCase() + "_" + payload.getTransactionId().trim();
        }
        if (payload.getOrderId() != null && !payload.getOrderId().isBlank()) {
            return provider.toUpperCase() + "_" + payload.getOrderId().trim() + "_" + System.currentTimeMillis();
        }
        return provider.toUpperCase() + "_" + UUID.randomUUID().toString();
    }
}
