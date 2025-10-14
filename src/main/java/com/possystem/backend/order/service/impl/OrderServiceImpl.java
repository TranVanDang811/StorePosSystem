package com.possystem.backend.order.service.impl;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
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
import com.possystem.backend.user.entity.CustomerProfile;
import com.possystem.backend.user.entity.User;
import com.possystem.backend.user.repository.CustomerProfileRepository;
import com.possystem.backend.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        // 🔹 1. Kiểm tra user
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        CustomerProfile profile = user.getCustomerProfile();
        if (profile == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        // 🔹 2. Tạo đơn hàng ban đầu
        Orders order = Orders.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .totalPrice(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(BigDecimal.ZERO)
                .usedPoints(0)
                .build();

        // 🔹 3. Tạo chi tiết đơn hàng
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

        // 🔹 4. Tính tổng tiền
        BigDecimal totalPrice = orderDetails.stream()
                .map(OrderDetail::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalPrice(totalPrice);
        order.setOrderDetails(orderDetails);

        // 🔹 5. Áp dụng mã giảm giá & điểm tích lũy
        discountService.applyDiscount(order, request.getDiscountCode(), request.getUsedPoints());

        // ✅ Nếu có điểm được dùng, trừ điểm trong hồ sơ khách hàng
        if (request.getUsedPoints() != null && request.getUsedPoints() > 0) {
            int usedPoints = request.getUsedPoints();
            if (profile.getLoyaltyPoints() < usedPoints) {
                throw new AppException(ErrorCode.NOT_ENOUGH_POINTS);
            }
            profile.setLoyaltyPoints(profile.getLoyaltyPoints() - usedPoints);
        }

        // 🔹 6. Cộng điểm thưởng (10% số tiền thực trả)
        int earnedPoints = order.getFinalAmount()
                .multiply(BigDecimal.valueOf(0.1))
                .setScale(0, RoundingMode.DOWN)
                .intValue();

        profile.setLoyaltyPoints(profile.getLoyaltyPoints() + earnedPoints);

        // 🔹 7. Lưu dữ liệu
        Orders savedOrder = orderRepository.save(order);
        customerProfileRepository.save(profile);

        // 🔹 8. Tính lại điểm còn lại
        int remainingPoints = profile.getLoyaltyPoints();

        // 🔹 9. Map sang response
        OrderResponse response = orderMapper.toResponse(savedOrder);
        response.setRemainingPoints(remainingPoints);
        response.setUsedPoints(request.getUsedPoints());
        response.setEarnedPoints(earnedPoints);

        return response;
    }


    public Page<OrderResponse> getAllOrders(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return orderRepository.findAll(pageable)
                .map(orderMapper::toResponse);
    }

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
    public ResponseEntity<ByteArrayResource> printOrderInvoice(String orderId)  {
        Orders order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, out);
        document.open();

        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Font normalFont = new Font(Font.HELVETICA, 12);

        document.add(new Paragraph("HÓA ĐƠN ĐƠN HÀNG #" + order.getId(), titleFont));
        document.add(new Paragraph("Khách hàng: " + order.getUser().getFullName(), normalFont));
        document.add(new Paragraph("Ngày đặt: " + order.getCreatedAt().toString(), normalFont));
        document.add(new Paragraph("Trạng thái: " + order.getStatus().name(), normalFont));
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.addCell("Tên sản phẩm");
        table.addCell("Giá");
        table.addCell("Số lượng");
        table.addCell("Thành tiền");

        for (OrderDetail detail : order.getOrderDetails()) {
            table.addCell(detail.getProduct().getName());
            table.addCell(detail.getProduct().getPrice().toString());
            table.addCell(String.valueOf(detail.getQuantity()));
            BigDecimal total = detail.getProduct().getPrice().multiply(BigDecimal.valueOf(detail.getQuantity()));
            table.addCell(total.toString());
        }

        document.add(table);
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Tổng tiền: " + order.getFinalAmount().toString(), titleFont));
        document.close();

        ByteArrayResource resource = new ByteArrayResource(out.toByteArray());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=order_" + orderId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }
}
