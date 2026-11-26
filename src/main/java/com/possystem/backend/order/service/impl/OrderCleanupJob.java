package com.possystem.backend.order.service.impl;

import com.possystem.backend.common.enums.OrderStatus;
import com.possystem.backend.common.observability.PosBusinessMetrics;
import com.possystem.backend.common.outbox.event.PosEventType;
import com.possystem.backend.common.outbox.service.OutboxPublisher;
import com.possystem.backend.order.entity.Orders;
import com.possystem.backend.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCleanupJob {

    private final OrderRepository orderRepository;
    private final OutboxPublisher outboxPublisher;
    private final PosBusinessMetrics businessMetrics;

    @Scheduled(fixedRate = 60000) // chạy mỗi 60s
    @Transactional
    public void deleteExpiredPendingOrders() {

        LocalDateTime expiredTime = LocalDateTime.now().minusMinutes(30);

        List<Orders> expiredOrders =
                orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, expiredTime);

        if (!expiredOrders.isEmpty()) {
            for (Orders order : expiredOrders) {
                order.setStatus(OrderStatus.CANCELED);
            }
            orderRepository.saveAll(expiredOrders);

            if (outboxPublisher != null) {
                for (Orders order : expiredOrders) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("orderId", order.getId());
                    payload.put("orderCode", order.getOrderCode());
                    payload.put("finalAmount", order.getFinalAmount());
                    payload.put("reason", "AUTO_EXPIRED_PENDING");
                    outboxPublisher.publishInCurrentTransaction(
                            "ORDER",
                            order.getId(),
                            PosEventType.ORDER_CANCELLED,
                            "order:" + order.getId(),
                            order.getOrderCode(),
                            payload);
                }
            }
            if (businessMetrics != null) {
                for (int i = 0; i < expiredOrders.size(); i++) {
                    businessMetrics.recordOrderCancelled("AUTO_EXPIRED");
                }
            }

            log.info("Cancelled {} expired PENDING orders", expiredOrders.size());
        }
    }
}