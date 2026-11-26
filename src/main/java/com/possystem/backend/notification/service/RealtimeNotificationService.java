package com.possystem.backend.notification.service;

import com.possystem.backend.notification.dto.RealtimeEventMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RealtimeNotificationService {

    SimpMessagingTemplate messagingTemplate;

    public void broadcastOrder(String eventType, Object orderData) {
        String destination = "/topic/orders";
        RealtimeEventMessage<Object> message = RealtimeEventMessage.builder()
                .eventType(eventType)
                .channel("ORDERS")
                .message("Cập nhật đơn hàng mới từ quầy POS")
                .timestamp(LocalDateTime.now())
                .payload(orderData)
                .build();

        try {
            messagingTemplate.convertAndSend(destination, message);
            log.info("Broadcasted live order event [{}] to {}", eventType, destination);
        } catch (Exception e) {
            log.warn("Không thể gửi WebSocket notification đơn hàng: {}", e.getMessage());
        }
    }

    public void broadcastPayment(String eventType, Object paymentData) {
        String destination = "/topic/payments";
        RealtimeEventMessage<Object> message = RealtimeEventMessage.builder()
                .eventType(eventType)
                .channel("PAYMENTS")
                .message("Giao dịch thanh toán thành công")
                .timestamp(LocalDateTime.now())
                .payload(paymentData)
                .build();

        try {
            messagingTemplate.convertAndSend(destination, message);
            log.info("Broadcasted live payment event [{}] to {}", eventType, destination);
        } catch (Exception e) {
            log.warn("Không thể gửi WebSocket notification thanh toán: {}", e.getMessage());
        }
    }

    public void broadcastInventoryAlert(String productId, String productName, int stock) {
        String destination = "/topic/inventory";
        RealtimeEventMessage<Object> message = RealtimeEventMessage.builder()
                .eventType("STOCK_ALERT")
                .channel("INVENTORY")
                .message("Cảnh báo tồn kho mặt hàng: " + productName + " (Còn lại: " + stock + ")")
                .timestamp(LocalDateTime.now())
                .payload(productId)
                .build();

        try {
            messagingTemplate.convertAndSend(destination, message);
            log.info("Broadcasted inventory alert to {}", destination);
        } catch (Exception e) {
            log.warn("Không thể gửi WebSocket notification kho: {}", e.getMessage());
        }
    }

    public void broadcastShiftUpdate(String eventType, Object shiftData) {
        String destination = "/topic/shifts";
        RealtimeEventMessage<Object> message = RealtimeEventMessage.builder()
                .eventType(eventType)
                .channel("SHIFTS")
                .message("Biến động ca làm việc thu ngân")
                .timestamp(LocalDateTime.now())
                .payload(shiftData)
                .build();

        try {
            messagingTemplate.convertAndSend(destination, message);
            log.info("Broadcasted shift event [{}] to {}", eventType, destination);
        } catch (Exception e) {
            log.warn("Không thể gửi WebSocket notification ca làm: {}", e.getMessage());
        }
    }
}
