package com.possystem.backend.order.service.impl;

import com.possystem.backend.common.enums.OrderStatus;
import com.possystem.backend.order.entity.Orders;
import com.possystem.backend.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCleanupJob {

    private final OrderRepository orderRepository;

    @Scheduled(fixedRate = 60000) // chạy mỗi 60s
    public void deleteExpiredPendingOrders() {

        LocalDateTime expiredTime = LocalDateTime.now().minusMinutes(5);

        List<Orders> expiredOrders =
                orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, expiredTime);

        if (!expiredOrders.isEmpty()) {

            orderRepository.deleteAll(expiredOrders);

            log.info("Deleted {} expired PENDING orders", expiredOrders.size());
        }
    }
}