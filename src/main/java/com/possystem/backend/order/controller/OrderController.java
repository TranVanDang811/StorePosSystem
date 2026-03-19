package com.possystem.backend.order.controller;

import com.possystem.backend.common.enums.OrderStatus;
import com.possystem.backend.common.response.ApiResponse;
import com.possystem.backend.order.dto.OrderRequest;
import com.possystem.backend.order.dto.OrderResponse;
import com.possystem.backend.order.dto.RevenueStatsResponse;
import com.possystem.backend.order.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Order Management", description = "Endpoints for managing and analyzing orders")
public class OrderController {
    OrderService orderService;

    //Create order
    @Operation(summary = "Create a new order", description = "Create a new customer order with products and quantities.")
    @PostMapping
    ApiResponse<OrderResponse> createOrder(@RequestBody OrderRequest request) {
        return ApiResponse.<OrderResponse>builder()
                .result(orderService.createOrder(request))
                .build();
    }

    //Order List
    @Operation(summary = "Get all orders", description = "Retrieve a paginated list of all orders in the system.")
    @GetMapping
    ApiResponse<Page<OrderResponse>> getAllOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size) {
        return ApiResponse.<Page<OrderResponse>>builder()
                .result(orderService.getAllOrders(page - 1, size))
                .build();
    }

    //List of orders by id
    @Operation(summary = "Get order by ID", description = "Retrieve detailed information about a specific order by its ID.")
    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getOrderById(@PathVariable String orderId) {
        return ApiResponse.<OrderResponse>builder()
                .result(orderService.getOrderById(orderId))
                .build();
    }

    @Operation(summary = "Delete order", description = "Delete an order by its ID.")
    //Delete order
    @DeleteMapping("/{orderId}")
    public ApiResponse<Void> deleteOrder(@PathVariable String orderId) {
        orderService.deleteOrder(orderId);
        return ApiResponse.<Void>builder().message("Delete successfully").build();
    }

    @Operation(summary = "Filter orders", description = "Filter orders by date range and optionally by status.")
    //List of orders by start end date and status
    @GetMapping("/filter")
    public List<OrderResponse> filterByDateRange(  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                   @RequestParam(value = "status", required = false) OrderStatus status) {
        return orderService.getOrdersByDateRange(start, end,status);
    }

    @Operation(summary = "Revenue statistics", description = "Get total revenue and order count between two dates.")
    //Revenue
    @GetMapping("/revenue")
    public RevenueStatsResponse revenueStats(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return orderService.getRevenueStats(start, end);
    }

    @PutMapping("/{id}/hold")
    public ApiResponse<String> holdOrder(@PathVariable String id) {

        orderService.holdOrder(id);

        return ApiResponse.<String>builder()
                .result("Order has been put on HOLD")
                .build();
    }

    @PutMapping("/{id}/update")
    public ApiResponse<OrderResponse> updateOrder(
            @PathVariable String id,
            @RequestBody OrderRequest request
    ) {

        OrderResponse response = orderService.updateOrder(id, request);

        return ApiResponse.<OrderResponse>builder()
                .result(response)
                .build();
    }

    @GetMapping("/status/{status}")
    public ApiResponse<Page<OrderResponse>> getOrdersByStatus(
            @PathVariable OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {

        return ApiResponse.<Page<OrderResponse>>builder()
                .result(orderService.getOrdersByStatus(status, page - 1, size))
                .build();
    }
}
