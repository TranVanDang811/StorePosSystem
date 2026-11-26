package com.possystem.backend.common.outbox.kafka;

import com.possystem.backend.common.outbox.event.DomainEventEnvelope;
import com.possystem.backend.common.outbox.event.PosEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaTopicResolver {

    private final KafkaProperties kafkaProperties;

    public String resolveTopic(DomainEventEnvelope envelope) {
        KafkaProperties.Topics topics = kafkaProperties.getTopics();
        if (envelope == null || envelope.getEventType() == null) {
            return topics.getOrders();
        }
        String eventType = envelope.getEventType();
        return switch (eventType) {
            case PosEventType.PAYMENT_COMPLETED,
                 PosEventType.PAYMENT_FAILED,
                 PosEventType.LEGACY_PAYMENT_SUCCESS -> topics.getPayments();
            case PosEventType.INVENTORY_UPDATED -> topics.getInventory();
            case PosEventType.LOYALTY_POINTS_EARNED -> topics.getLoyalty();
            case PosEventType.VOUCHER_REDEEMED -> topics.getVouchers();
            case PosEventType.SHIFT_CLOSED -> topics.getShifts();
            default -> topics.getOrders();
        };
    }
}
