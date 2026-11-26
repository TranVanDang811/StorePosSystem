package com.possystem.backend.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.possystem.backend.common.enums.OrderStatus;
import com.possystem.backend.common.enums.PaymentMethod;
import com.possystem.backend.common.enums.PaymentStatus;
import com.possystem.backend.common.enums.ShiftStatus;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.util.mapper.OrderMapper;
import com.possystem.backend.common.util.mapper.PaymentMapper;
import com.possystem.backend.discount.service.DiscountService;
import com.possystem.backend.order.entity.OrderDetail;
import com.possystem.backend.order.entity.Orders;
import com.possystem.backend.order.repository.OrderRepository;
import com.possystem.backend.payment.dto.PaymentRequest;
import com.possystem.backend.payment.dto.PaymentResponse;
import com.possystem.backend.payment.entity.Payment;
import com.possystem.backend.payment.repository.PaymentRepository;
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.product.repository.StockMovementRepository;
import com.possystem.backend.shift.entity.Shift;
import com.possystem.backend.shift.repository.ShiftRepository;
import com.possystem.backend.user.repository.CustomerProfileRepository;
import com.possystem.backend.user.repository.LoyaltyTransactionRepository;
import com.possystem.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private DiscountService discountService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private LoyaltyTransactionRepository loyaltyTransactionRepository;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Orders testOrder;
    private Product testProduct;
    private OrderDetail testDetail;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
                .productCode("PROD-01")
                .name("Trà đào cam sả")
                .price(BigDecimal.valueOf(35000))
                .stock(10)
                .build();
        testProduct.setId("prod-1");

        testOrder = Orders.builder()
                .orderCode("ORD-101")
                .status(OrderStatus.PENDING)
                .totalPrice(BigDecimal.valueOf(70000))
                .finalAmount(BigDecimal.valueOf(70000))
                .build();
        testOrder.setId("order-1");

        testDetail = OrderDetail.builder()
                .order(testOrder)
                .product(testProduct)
                .quantity(2)
                .totalPrice(BigDecimal.valueOf(70000))
                .build();

        testOrder.setOrderDetails(Set.of(testDetail));
    }

    @Test
    @DisplayName("Unit Test (Shift Guard): Chặn thanh toán nếu chưa có ca làm việc mở")
    void createPayment_NoOpenedShift_ThrowsException() {
        PaymentRequest request = PaymentRequest.builder()
                .orderId("order-1")
                .method(PaymentMethod.CASH)
                .cashReceived(BigDecimal.valueOf(100000))
                .build();

        when(shiftRepository.findByStatus(ShiftStatus.OPEN)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> paymentService.createPayment(request));
        assertEquals(ErrorCode.NO_OPENED_SHIFT, ex.getErrorCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("Unit Test: Thanh toán tiền mặt thành công, trừ tồn kho và cập nhật đơn sang PAID")
    void createPayment_Success() {
        PaymentRequest request = PaymentRequest.builder()
                .orderId("order-1")
                .method(PaymentMethod.CASH)
                .cashReceived(BigDecimal.valueOf(100000))
                .build();

        when(shiftRepository.findByStatus(ShiftStatus.OPEN)).thenReturn(Optional.of(Shift.builder().status(ShiftStatus.OPEN).build()));
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(testOrder));
        when(paymentRepository.findByOrder_Id("order-1")).thenReturn(null);
        when(productRepository.findAllByIdWithLock(any())).thenReturn(java.util.List.of(testProduct));
        when(productRepository.findAllByIdWithLock(any())).thenReturn(java.util.List.of(testProduct));

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentMapper.toPaymentResponse(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            return PaymentResponse.builder()
                    .amount(p.getAmount())
                    .status(p.getStatus())
                    .cashReceived(p.getCashReceived())
                    .changeAmount(p.getChangeAmount())
                    .build();
        });

        PaymentResponse response = paymentService.createPayment(request);

        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(70000), response.getAmount());
        assertEquals(BigDecimal.valueOf(30000), response.getChangeAmount());
        assertEquals(OrderStatus.PAID, testOrder.getStatus());
        assertEquals(8, testProduct.getStock()); // Tồn kho ban đầu 10 - 2 = 8

        verify(productRepository, times(1)).save(testProduct);
        verify(orderRepository, times(1)).save(testOrder);
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    @DisplayName("Unit Test: Ném ngoại lệ khi tồn kho không đủ để thanh toán")
    void createPayment_OutOfStock_ThrowsException() {
        testProduct.setStock(1); // Chỉ còn 1 cái trong kho nhưng đơn mua 2
        PaymentRequest request = PaymentRequest.builder()
                .orderId("order-1")
                .method(PaymentMethod.CASH)
                .cashReceived(BigDecimal.valueOf(100000))
                .build();

        when(shiftRepository.findByStatus(ShiftStatus.OPEN)).thenReturn(Optional.of(Shift.builder().status(ShiftStatus.OPEN).build()));
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(testOrder));
        when(paymentRepository.findByOrder_Id("order-1")).thenReturn(null);
        when(productRepository.findAllByIdWithLock(any())).thenReturn(java.util.List.of(testProduct));
        when(productRepository.findAllByIdWithLock(any())).thenReturn(java.util.List.of(testProduct));

        AppException ex = assertThrows(AppException.class, () -> paymentService.createPayment(request));
        assertEquals(ErrorCode.PRODUCT_OUT_OF_STOCK, ex.getErrorCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }
}
