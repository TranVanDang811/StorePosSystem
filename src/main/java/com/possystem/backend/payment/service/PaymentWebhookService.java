package com.possystem.backend.payment.service;

import com.possystem.backend.payment.dto.WebhookPaymentPayload;
import com.possystem.backend.payment.dto.WebhookResult;
import com.possystem.backend.payment.entity.WebhookEvent;

import java.util.List;
import java.util.Map;

public interface PaymentWebhookService {
    WebhookResult processWebhook(String provider, String idempotencyKey, WebhookPaymentPayload payload);
    Map<String, Object> reconcilePendingWebhooks();
    List<WebhookEvent> getRecentWebhookEvents(int limit);
}
