package com.possystem.backend.order.controller;

import com.possystem.backend.common.enums.OrderStatus;
import com.possystem.backend.common.response.ApiResponse;
import com.possystem.backend.order.dto.OrderRequest;
import com.possystem.backend.order.dto.OrderResponse;
import com.possystem.backend.order.dto.OrderSummaryResponse;
import com.possystem.backend.order.dto.RevenueStatsResponse;
import com.possystem.backend.order.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<OrderResponse> createOrder(@RequestBody @Valid OrderRequest request) {
        return ApiResponse.<OrderResponse>builder()
                .result(orderService.createOrder(request))
                .build();
    }

    //Order List
    @Operation(summary = "Get all orders", description = "Retrieve a paginated list of all orders in the system.")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    ApiResponse<Page<OrderResponse>> getAllOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.<Page<OrderResponse>>builder()
                .result(orderService.getOrders(page - 1, size, fromDate, toDate, status, keyword))
                .build();
    }

    @Operation(summary = "Get order summary stats", description = "Retrieve aggregate statistics for orders matching filters.")
    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public ApiResponse<OrderSummaryResponse> getOrderSummary(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.<OrderSummaryResponse>builder()
                .result(orderService.getOrderSummary(fromDate, toDate, status, keyword))
                .build();
    }

    //List of orders by id
    @Operation(summary = "Get order by ID", description = "Retrieve detailed information about a specific order by its ID.")
    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public ApiResponse<OrderResponse> getOrderById(@PathVariable String orderId) {
        return ApiResponse.<OrderResponse>builder()
                .result(orderService.getOrderById(orderId))
                .build();
    }

    @Operation(summary = "Delete order", description = "Delete an order by its ID.")
    //Delete order
    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public ApiResponse<Void> deleteOrder(@PathVariable String orderId) {
        orderService.deleteOrder(orderId);
        return ApiResponse.<Void>builder().message("Delete successfully").build();
    }

    @Operation(summary = "Filter orders", description = "Filter orders by date range and optionally by status.")
    //List of orders by start end date and status
    @GetMapping("/filter")
    public ApiResponse<List<OrderResponse>> filterByDateRange(  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                   @RequestParam(value = "status", required = false) OrderStatus status) {
        return ApiResponse.<List<OrderResponse>>builder().result(orderService.getOrdersByDateRange(start, end, status)).build();
    }

    @Operation(summary = "Revenue statistics", description = "Get total revenue and order count between two dates.")
    //Revenue
    @GetMapping("/revenue")
    public ApiResponse<RevenueStatsResponse> revenueStats(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ApiResponse.<RevenueStatsResponse>builder().result(orderService.getRevenueStats(start, end)).build();
    }

    @PutMapping("/{id}/hold")
    public ApiResponse<String> holdOrder(@PathVariable String id) {

        orderService.holdOrder(id);

        return ApiResponse.<String>builder()
                .result("Order has been put on HOLD")
                .build();
    }

    @Operation(summary = "Cancel an unpaid order", description = "Cancel a PENDING or HOLD order and emit OrderCancelled domain event.")
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE') or hasRole('EMPLOYEE')")
    public ApiResponse<OrderResponse> cancelOrder(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "CASHIER_CANCELLED") String reason) {
        return ApiResponse.<OrderResponse>builder()
                .result(orderService.cancelOrder(id, reason))
                .build();
    }

    @PutMapping("/{id}/update")
    public ApiResponse<OrderResponse> updateOrder(
            @PathVariable String id,
            @RequestBody @Valid OrderRequest request
    ) {

        OrderResponse response = orderService.updateOrder(id, request);

        return ApiResponse.<OrderResponse>builder()
                .result(response)
                .build();
    }

    @GetMapping("/status/{status}")
    public ApiResponse<Page<OrderResponse>> getOrdersByStatus(
            @PathVariable OrderStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size
    ) {

        return ApiResponse.<Page<OrderResponse>>builder()
                .result(orderService.getOrdersByStatus(status, Math.max(0, page - 1), size))
                .build();
    }
}
