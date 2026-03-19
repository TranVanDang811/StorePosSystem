package com.possystem.backend.order.service.impl;

import com.possystem.backend.common.enums.OrderStatus;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.util.mapper.OrderDetailMapper;
import com.possystem.backend.common.util.mapper.OrderMapper;
import com.possystem.backend.discount.repository.DiscountRepository;
import com.possystem.backend.discount.service.DiscountService;
import com.possystem.backend.order.dto.OrderRequest;
import com.possystem.backend.order.dto.OrderResponse;
import com.possystem.backend.order.dto.RevenueStatsResponse;
import com.possystem.backend.order.entity.OrderDetail;
import com.possystem.backend.order.entity.Orders;
import com.possystem.backend.order.repository.OrderDetailRepository;
import com.possystem.backend.order.repository.OrderRepository;
import com.possystem.backend.order.service.OrderService;
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.user.repository.CustomerProfileRepository;
import com.possystem.backend.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderServiceImpl implements OrderService {
    final OrderRepository orderRepository;
    final OrderDetailRepository orderDetailRepository;
    final UserRepository userRepository;
    final ProductRepository productRepository;
    final OrderMapper orderMapper;
    final OrderDetailMapper orderDetailMapper;
    final DiscountRepository discountRepository;
    DiscountService discountService;
    CustomerProfileRepository customerProfileRepository;

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {

        // Khởi tạo đơn hàng
        Orders order = Orders.builder()
                .orderCode(generateOrderCode())
                .status(OrderStatus.PENDING)
                .totalPrice(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(BigDecimal.ZERO)
                .build();

        // Tính tổng tiền sản phẩm
        Set<OrderDetail> orderDetails = request.getOrderDetails().stream()
                .map(detailRequest -> {
                    Product product = productRepository.findById(detailRequest.getProductId())
                            .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

                    BigDecimal totalPrice = product.getPrice()
                            .multiply(BigDecimal.valueOf(detailRequest.getQuantity()));

                    return OrderDetail.builder()
                            .order(order)
                            .product(product)
                            .quantity(detailRequest.getQuantity())
                            .totalPrice(totalPrice)
                            .build();
                })
                .collect(Collectors.toSet());

        BigDecimal totalPrice = orderDetails.stream()
                .map(OrderDetail::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setOrderDetails(orderDetails);
        order.setTotalPrice(totalPrice);

        // Áp mã giảm giá
        discountService.applyDiscountCode(order, request.getDiscountCode());

        // Tính tiền cuối
        if (order.getFinalAmount() == null || order.getFinalAmount().compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal finalAmount = order.getTotalPrice()
                    .subtract(order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO);

            order.setFinalAmount(finalAmount.max(BigDecimal.ZERO));
        }

        // Lưu đơn hàng
        Orders savedOrder = orderRepository.save(order);

        // Response
        return orderMapper.toResponse(savedOrder);
    }


    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public Page<OrderResponse> getAllOrders(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return orderRepository.findAll(pageable)
                .map(orderMapper::toResponse);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public OrderResponse getOrderById(String orderId) {
        Orders order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        return orderMapper.toResponse(order);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE') or hasRole('EMPLOYEE')")
    @Transactional
    public void deleteOrder(String orderId) {
        Orders order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        orderRepository.delete(order);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE') or hasRole('EMPLOYEE')")
    public List<OrderResponse> getOrdersByDateRange(LocalDateTime start, LocalDateTime end , OrderStatus status) {
        List<Orders> orders = orderRepository.findAllByCreatedAtBetween(start, end);

        if (status != null) {
            orders = orders.stream()
                    .filter(order -> order.getStatus() == status)
                    .toList();
        }

        return orders.stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    //py
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE') or hasRole('EMPLOYEE')")
    public RevenueStatsResponse getRevenueStats(LocalDateTime start, LocalDateTime end) {
        List<Orders> orders = orderRepository.findAllByCreatedAtBetween(start, end).stream()
                .filter(order -> order.getStatus().name().equals("PAID"))
                .toList();

        BigDecimal totalRevenue = orders.stream()
                .map(Orders::getFinalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalProductsSold = orders.stream()
                .flatMap(order -> order.getOrderDetails().stream())
                .mapToInt(OrderDetail::getQuantity)
                .sum();

        return new RevenueStatsResponse((long) orders.size(), totalRevenue, totalProductsSold);
    }


    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE') or hasRole('EMPLOYEE')")
    @Transactional
    public void holdOrder(String orderId) {

        Orders order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        order.setStatus(OrderStatus.HOLD);

        orderRepository.save(order);
    }


    @Transactional
    public OrderResponse updateOrder(String orderId, OrderRequest request) {

        Orders order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        if(order.getStatus() == OrderStatus.PAID){
            throw new AppException(ErrorCode.ORDER_ALREADY_PAID);
        }

        order.getOrderDetails().clear();

        Set<OrderDetail> details = request.getOrderDetails()
                .stream()
                .map(d -> {
                    Product product = productRepository.findById(d.getProductId())
                            .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

                    return OrderDetail.builder()
                            .order(order)
                            .product(product)
                            .quantity(d.getQuantity())
                            .build();
                }).collect(Collectors.toSet());

        order.setOrderDetails(details);

        return orderMapper.toResponse(orderRepository.save(order));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE') or hasRole('EMPLOYEE')")
    public Page<OrderResponse> getOrdersByStatus(OrderStatus status, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        return orderRepository
                .findByStatus(status, pageable)
                .map(orderMapper::toResponse);
    }

    private String generateOrderCode() {

        String date = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        String prefix = "ORD-" + date + "-";

        List<Orders> lastOrders =
                orderRepository.findLastOrderCodeOfDay(prefix, PageRequest.of(0,1));

        if(lastOrders.isEmpty()){
            return prefix + "0001";
        }

        String lastCode = lastOrders.get(0).getOrderCode();

        String numberPart = lastCode.substring(lastCode.lastIndexOf("-") + 1);

        int nextNumber = Integer.parseInt(numberPart) + 1;

        return prefix + String.format("%04d", nextNumber);
    }
}
