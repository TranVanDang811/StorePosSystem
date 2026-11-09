package com.possystem.backend.order.service.impl;

import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.util.mapper.OrderMapper;
import com.possystem.backend.discount.service.DiscountService;
import com.possystem.backend.order.dto.OrderDetailRequest;
import com.possystem.backend.order.dto.OrderRequest;
import com.possystem.backend.order.dto.OrderResponse;
import com.possystem.backend.order.entity.Orders;
import com.possystem.backend.order.repository.OrderDetailRepository;
import com.possystem.backend.order.repository.OrderRepository;
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderDetailRepository orderDetailRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private DiscountService discountService;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Product productA;
    private Product productB;

    @BeforeEach
    void setUp() {
        productA = Product.builder()
                .productCode("PROD-001")
                .name("Cà phê sữa đá")
                .price(BigDecimal.valueOf(25000))
                .stock(50)
                .build();
        productA.setId("prod-1");

        productB = Product.builder()
                .productCode("PROD-002")
                .name("Bánh mì que")
                .price(BigDecimal.valueOf(15000))
                .stock(30)
                .build();
        productB.setId("prod-2");
    }

    @Test
    @DisplayName("Unit Test: Tạo đơn hàng thành công và tính tổng tiền chính xác")
    void createOrder_Success() {
        OrderDetailRequest item1 = OrderDetailRequest.builder()
                .productId("prod-1")
                .quantity(2) // 2 * 25000 = 50000
                .build();
        OrderDetailRequest item2 = OrderDetailRequest.builder()
                .productId("prod-2")
                .quantity(1) // 1 * 15000 = 15000
                .build();

        OrderRequest request = OrderRequest.builder()
                .orderDetails(List.of(item1, item2))
                .build();

        when(productRepository.findById("prod-1")).thenReturn(Optional.of(productA));
        when(productRepository.findById("prod-2")).thenReturn(Optional.of(productB));

        when(orderRepository.save(any(Orders.class))).thenAnswer(invocation -> {
            Orders order = invocation.getArgument(0);
            order.setId("order-123");
            return order;
        });

        when(orderMapper.toResponse(any(Orders.class))).thenAnswer(invocation -> {
            Orders order = invocation.getArgument(0);
            return OrderResponse.builder()
                    .orderCode(order.getOrderCode())
                    .totalPrice(order.getTotalPrice())
                    .finalAmount(order.getFinalAmount())
                    .build();
        });

        OrderResponse response = orderService.createOrder(request);

        assertNotNull(response);
        // Tổng tiền kỳ vọng: 50000 + 15000 = 65000
        assertEquals(BigDecimal.valueOf(65000), response.getTotalPrice());
        assertEquals(BigDecimal.valueOf(65000), response.getFinalAmount());
        verify(orderRepository, times(1)).save(any(Orders.class));
    }

    @Test
    @DisplayName("Unit Test: Ném ngoại lệ khi sản phẩm trong đơn không tồn tại (Rollback transaction)")
    void createOrder_ProductNotFound_ThrowsException() {
        OrderDetailRequest item = OrderDetailRequest.builder()
                .productId("invalid-prod-id")
                .quantity(1)
                .build();

        OrderRequest request = OrderRequest.builder()
                .orderDetails(List.of(item))
                .build();

        when(productRepository.findById("invalid-prod-id")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                orderService.createOrder(request)
        );

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, ex.getErrorCode());
        verify(orderRepository, never()).save(any(Orders.class));
    }
}

