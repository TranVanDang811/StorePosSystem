package com.possystem.backend.order.controller;

import com.possystem.backend.common.enums.OrderStatus;
import com.possystem.backend.common.response.ApiResponse;
import com.possystem.backend.order.dto.OrderRequest;
import com.possystem.backend.order.dto.OrderResponse;
import com.possystem.backend.order.dto.RevenueStatsResponse;
import com.possystem.backend.order.service.OrderService;
import com.possystem.backend.product.dto.ProductResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderController {
    OrderService orderService;

    //Create order
    @PostMapping
    ApiResponse<OrderResponse> createOrder(@RequestBody OrderRequest request) {
        return ApiResponse.<OrderResponse>builder()
                .result(orderService.createOrder(request))
                .build();
    }

    //Order List
    @GetMapping
    ApiResponse<Page<OrderResponse>> getAllOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size) {
        return ApiResponse.<Page<OrderResponse>>builder()
                .result(orderService.getAllOrders(page - 1, size))
                .build();
    }

    //List of orders by id
    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getOrderById(@PathVariable String orderId) {
        return ApiResponse.<OrderResponse>builder()
                .result(orderService.getOrderById(orderId))
                .build();
    }

    //Delete order
    @DeleteMapping("/{orderId}")
    public ApiResponse<Void> deleteOrder(@PathVariable String orderId) {
        orderService.deleteOrder(orderId);
        return ApiResponse.<Void>builder().message("Delete successfully").build();
    }

    //List of orders by start end date and status
    @GetMapping("/filter")
    public List<OrderResponse> filterByDateRange(  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                   @RequestParam(value = "status", required = false) OrderStatus status) {
        return orderService.getOrdersByDateRange(start, end,status);
    }

    //Revenue
    @GetMapping("/revenue")
    public RevenueStatsResponse revenueStats(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return orderService.getRevenueStats(start, end);
    }

    //Print check
    @GetMapping("/print/{orderId}")
    public ResponseEntity<ByteArrayResource> printOrder(@PathVariable String orderId) throws IOException {
        return orderService.printOrderInvoice(orderId);
    }
}
