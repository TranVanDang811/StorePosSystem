package com.possystem.backend.common.outbox.consumer;

import com.possystem.backend.common.outbox.event.DomainEventEnvelope;
import com.possystem.backend.common.outbox.event.PosEventType;
import com.possystem.backend.notification.service.RealtimeNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Asynchronous real-time notification dispatcher routing domain events to STOMP WebSocket topics.
 */
@Slf4j
@Order(2)
@Component
@RequiredArgsConstructor
public class NotificationConsumer implements PosEventConsumer {

    private final RealtimeNotificationService realtimeNotificationService;

    @Override
    public String consumerName() {
        return "NotificationConsumer";
    }

    @Override
    public boolean supports(String eventType) {
        return eventType != null && !eventType.isBlank();
    }

    @Override
    public void consume(DomainEventEnvelope envelope) {
        String eventType = envelope.getEventType();
        Map<String, Object> payload = envelope.getPayload() != null ? envelope.getPayload() : Map.of();

        switch (eventType) {
            case PosEventType.ORDER_CREATED,
                 PosEventType.ORDER_COMPLETED,
                 PosEventType.ORDER_CANCELLED,
                 PosEventType.LEGACY_ORDER_CREATED ->
                    realtimeNotificationService.broadcastOrder(eventType, payload);

            case PosEventType.PAYMENT_COMPLETED,
                 PosEventType.PAYMENT_FAILED,
                 PosEventType.LEGACY_PAYMENT_SUCCESS ->
                    realtimeNotificationService.broadcastPayment(eventType, payload);

            case PosEventType.INVENTORY_UPDATED -> {
                String productId = String.valueOf(payload.getOrDefault("productId", envelope.getAggregateId()));
                String productName = String.valueOf(payload.getOrDefault("productName", "Mặt hàng POS"));
                int afterStock = parseInt(payload.get("afterStock"));
                realtimeNotificationService.broadcastInventoryAlert(productId, productName, afterStock);
            }

            case PosEventType.SHIFT_CLOSED ->
                    realtimeNotificationService.broadcastShiftUpdate(eventType, payload);

            case PosEventType.VOUCHER_REDEEMED,
                 PosEventType.LOYALTY_POINTS_EARNED ->
                    realtimeNotificationService.broadcastPayment(eventType, payload);

            default ->
                    realtimeNotificationService.broadcastOrder(eventType, payload);
        }
    }

    private int parseInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
