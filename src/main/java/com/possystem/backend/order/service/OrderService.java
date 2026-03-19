package com.possystem.backend.order.service;

import com.possystem.backend.common.enums.OrderStatus;
import com.possystem.backend.order.dto.OrderRequest;
import com.possystem.backend.order.dto.OrderResponse;
import com.possystem.backend.order.dto.RevenueStatsResponse;
import org.springframework.data.domain.Page;
import java.time.LocalDateTime;
import java.util.List;


public interface OrderService {
    OrderResponse createOrder(OrderRequest request);
    Page<OrderResponse> getAllOrders(int page, int size);
    OrderResponse getOrderById(String orderId);
    void deleteOrder(String orderId);
    List<OrderResponse> getOrdersByDateRange(LocalDateTime start, LocalDateTime end , OrderStatus status);
    RevenueStatsResponse getRevenueStats(LocalDateTime start, LocalDateTime end);
    void holdOrder(String orderId);
    OrderResponse updateOrder(String orderId, OrderRequest request);
    Page<OrderResponse> getOrdersByStatus(OrderStatus status, int page, int size) ;
}
