package com.possystem.backend.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.possystem.backend.common.enums.OrderStatus;
import com.possystem.backend.common.enums.PaymentMethod;
import com.possystem.backend.common.enums.PaymentStatus;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.util.mapper.OrderMapper;
import com.possystem.backend.common.util.mapper.PaymentMapper;
import com.possystem.backend.discount.dto.PointDiscountResult;
import com.possystem.backend.discount.service.DiscountService;
import com.possystem.backend.order.dto.OrderResponse;
import com.possystem.backend.order.dto.OrderDetailResponse;
import com.possystem.backend.order.entity.OrderDetail;
import com.possystem.backend.order.entity.Orders;
import com.possystem.backend.order.repository.OrderRepository;
import com.possystem.backend.payment.dto.PaymentRequest;
import com.possystem.backend.payment.dto.PaymentResponse;
import com.possystem.backend.payment.dto.RefundRecord;
import com.possystem.backend.payment.dto.RefundRequest;
import com.possystem.backend.payment.entity.Payment;
import com.possystem.backend.payment.repository.PaymentRepository;
import com.possystem.backend.payment.service.PaymentService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentServiceImpl implements PaymentService {
    PaymentRepository paymentRepository;
    OrderRepository orderRepository;
    PaymentMapper paymentMapper;
    OrderMapper orderMapper;
    final UserRepository userRepository;
    CustomerProfileRepository customerProfileRepository;
    final ObjectMapper objectMapper;
    final DiscountService discountService;
    ProductRepository productRepository;

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {

        User user = null;
        CustomerProfile profile = null;
        // =========================
        if (request.getUserPhone() != null && !request.getUserPhone().isBlank()) {

            user = userRepository.findByPhone(request.getUserPhone())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            profile = user.getCustomerProfile();
        }

        // =========================

        Orders order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (paymentRepository.findByOrder_Id(order.getId()) != null) {
            throw new AppException(ErrorCode.PAYMENT_ALREADY_EXISTS);
        }

        BigDecimal orderFinalAmount = order.getFinalAmount();

        if (orderFinalAmount == null || orderFinalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.INVALID_ORDER_AMOUNT);
        }


        // =========================

        int actualUsedPoints = 0;
        BigDecimal pointDiscount = BigDecimal.ZERO;

        if (profile != null && request.getUsedPoints() != null) {

            PointDiscountResult result = discountService.applyPointDiscount(
                    profile,
                    request.getUsedPoints(),
                    orderFinalAmount
            );

            pointDiscount = result.getDiscountAmount();
            actualUsedPoints = result.getUsedPoints();
        }


        // =========================

        BigDecimal paymentAmount = orderFinalAmount.subtract(pointDiscount);

        if (paymentAmount.compareTo(BigDecimal.ZERO) < 0) {
            paymentAmount = BigDecimal.ZERO;
        }

        // =========================

        if (request.getMethod() == PaymentMethod.CASH) {

            if (request.getCashReceived() == null ||
                    request.getCashReceived().compareTo(paymentAmount) < 0) {

                throw new AppException(ErrorCode.INVALID_PAYMENT_AMOUNT,
                        "Khách đưa không đủ tiền");
            }
        }

        // =========================

        BigDecimal changeAmount = BigDecimal.ZERO;

        if (request.getMethod() == PaymentMethod.CASH) {
            changeAmount = request.getCashReceived().subtract(paymentAmount);
        }


        // =========================

        int earnedPoints = 0;
        int remainingPoints = 0;

        if (profile != null) {

            earnedPoints = paymentAmount
                    .multiply(BigDecimal.valueOf(0.01))
                    .setScale(0, RoundingMode.DOWN)
                    .intValue();

            profile.setLoyaltyPoints(profile.getLoyaltyPoints() + earnedPoints);

            customerProfileRepository.save(profile);

            remainingPoints = profile.getLoyaltyPoints();
        }

        // =========================

        Payment payment = Payment.builder()
                .order(order)
                .user(user)
                .amount(paymentAmount)
                .pointDiscount(pointDiscount)
                .cashReceived(request.getCashReceived())
                .changeAmount(changeAmount)
                .method(request.getMethod())
                .status(PaymentStatus.SUCCESS)
                .paymentDate(LocalDateTime.now())
                .transactionId("TXN-" + System.currentTimeMillis())
                .note(request.getNote())
                .earnedPoints(earnedPoints)
                .usedPoints(actualUsedPoints)
                .build();

        paymentRepository.save(payment);


        // =========================
        for (OrderDetail detail : order.getOrderDetails()) {

            Product product = detail.getProduct();

            int newStock = product.getStock() - detail.getQuantity();

            if (newStock < 0) {
                throw new AppException(ErrorCode.PRODUCT_OUT_OF_STOCK);
            }

            product.setStock(newStock);

            // nếu hết hàng → đổi trạng thái
            if (newStock == 0) {
                product.setStatus(ProductStatus.OUT_OF_STOCK);
            }

            productRepository.save(product);
        }
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);


        // =========================

        PaymentResponse response = paymentMapper.toPaymentResponse(payment);

        response.setEarnedPoints(earnedPoints);
        response.setRemainingPoints(remainingPoints);
        response.setUsedPoints(actualUsedPoints);


        return response;
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public PaymentResponse getByOrderId(String orderId) {
        Payment payment = paymentRepository.findByOrder_Id(orderId);
        if (payment == null) {
            throw new AppException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return paymentMapper.toPaymentResponse(payment);
    }
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public PaymentResponse getPaymentById(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        return paymentMapper.toPaymentResponse(payment);
    }
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public Page<PaymentResponse> getAllPayments(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return paymentRepository.findAll(pageable)
                .map(paymentMapper::toPaymentResponse);
    }


    //--------
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    @Transactional
    public PaymentResponse refundPayment(String paymentId, RefundRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getMethod() != PaymentMethod.CASH) {
            throw new AppException(ErrorCode.PAYMENT_NOT_REFUNDABLE);
        }


        if (payment.getStatus() != PaymentStatus.SUCCESS && payment.getStatus() != PaymentStatus.REFUNDED) {
            throw new AppException(ErrorCode.PAYMENT_NOT_REFUNDABLE);
        }

        BigDecimal newTotalRefunded = payment.getTotalRefunded() == null
                ? request.getRefundAmount()
                : payment.getTotalRefunded().add(request.getRefundAmount());

        if (newTotalRefunded.compareTo(payment.getAmount()) > 0) {
            throw new AppException(ErrorCode.INVALID_REFUND_AMOUNT);
        }


        List<RefundRecord> refundRecords = new ArrayList<>();
        if (payment.getRefundHistoryJson() != null) {
            try {
                refundRecords = Arrays.asList(objectMapper.readValue(payment.getRefundHistoryJson(), RefundRecord[].class));
                refundRecords = new ArrayList<>(refundRecords);
            } catch (Exception e) {
                refundRecords = new ArrayList<>();
            }
        }


        refundRecords.add(RefundRecord.builder()
                .amount(request.getRefundAmount())
                .reason(request.getReason())
                .date(LocalDateTime.now())
                .build());

        payment.setTotalRefunded(newTotalRefunded);
        payment.setRefundHistoryJson(writeAsJson(refundRecords));

        if (newTotalRefunded.compareTo(payment.getAmount()) == 0) {
            Orders order = payment.getOrder();

            for (OrderDetail detail : order.getOrderDetails()) {

                Product product = detail.getProduct();

                int newStock = product.getStock() + detail.getQuantity();

                product.setStock(newStock);

                // nếu có hàng lại → ACTIVE
                if (product.getStatus() == ProductStatus.OUT_OF_STOCK && newStock > 0) {
                    product.setStatus(ProductStatus.ACTIVE);
                }

                productRepository.save(product);
            }
            payment.setStatus(PaymentStatus.REFUNDED);
        }
        paymentRepository.save(payment);
        return paymentMapper.toPaymentResponse(payment);
    }

    private String writeAsJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public List<RefundRecord> getRefundHistory(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getRefundHistoryJson() == null) {
            return List.of();
        }

        try {
            return Arrays.asList(objectMapper.readValue(payment.getRefundHistoryJson(), RefundRecord[].class));
        } catch (Exception e) {
            throw new AppException(ErrorCode.JSON_PARSE_ERROR);
        }
    }

    //----
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    @Transactional
    public ResponseEntity<ByteArrayResource> exportInvoiceByPayment(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        Orders order = payment.getOrder();
        OrderResponse orderResponse = orderMapper.toResponse(order);
        PaymentResponse paymentResponse = paymentMapper.toPaymentResponse(payment);

        byte[] pdfBytes = generatePaymentInvoice(paymentResponse, orderResponse);

        ByteArrayResource resource = new ByteArrayResource(pdfBytes);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoice-" + paymentId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }
    private byte[] generatePaymentInvoice(PaymentResponse payment, OrderResponse order) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            // Font
            Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD);
            Font sectionFont = new Font(Font.HELVETICA, 14, Font.BOLD);
            Font infoFont = new Font(Font.HELVETICA, 12, Font.NORMAL);
            Font boldFont = new Font(Font.HELVETICA, 12, Font.BOLD);

            // Tiêu đề
            Paragraph title = new Paragraph("HÓA ĐƠN THANH TOÁN ĐẲNG CẤP COFFEE", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(15);
            document.add(title);

            // Thông tin chung
            PdfPTable infoTable = new PdfPTable(1);
            infoTable.setWidthPercentage(100);
            infoTable.setSpacingAfter(10);
            infoTable.getDefaultCell().setBorder(Rectangle.NO_BORDER);

            infoTable.addCell(new Phrase("Mã thanh toán: " + payment.getId(), infoFont));
            infoTable.addCell(new Phrase("Ngày thanh toán: " +
                    payment.getPaymentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), infoFont));

            infoTable.addCell(new Phrase("Khách hàng: " + payment.getFullName(), infoFont));
            infoTable.addCell(new Phrase("Phương thức: " + payment.getMethod(), infoFont));

            infoTable.addCell(new Phrase("Trạng thái: " + payment.getStatus(), infoFont));

            document.add(infoTable);

            // Bảng sản phẩm
            Paragraph productHeader = new Paragraph("Chi tiết sản phẩm", sectionFont);
            productHeader.setSpacingBefore(10);
            productHeader.setSpacingAfter(8);
            document.add(productHeader);

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{4f, 1.2f, 2f, 2f});

            addStyledTableHeader(table, "Sản phẩm", "SL", "Giá", "Thành tiền");

            BigDecimal total = BigDecimal.ZERO;
            for (OrderDetailResponse detail : order.getOrderDetails()) {
                BigDecimal subtotal = detail.getPrice().multiply(BigDecimal.valueOf(detail.getQuantity()));

                table.addCell(createCell(detail.getName(), infoFont, Element.ALIGN_LEFT));
                table.addCell(createCell(String.valueOf(detail.getQuantity()), infoFont, Element.ALIGN_CENTER));
                table.addCell(createCell(formatCurrency(detail.getPrice()), infoFont, Element.ALIGN_RIGHT));
                table.addCell(createCell(formatCurrency(subtotal), infoFont, Element.ALIGN_RIGHT));

                total = total.add(subtotal);
            }
            document.add(table);

            // Tổng tiền & giảm giá
            Paragraph summaryHeader = new Paragraph("Tổng kết thanh toán", sectionFont);
            summaryHeader.setSpacingBefore(15);
            summaryHeader.setSpacingAfter(8);
            document.add(summaryHeader);

            PdfPTable summary = new PdfPTable(2);
            summary.setWidthPercentage(60);
            summary.setHorizontalAlignment(Element.ALIGN_RIGHT);
            summary.setSpacingAfter(10);

            // Không viền, không khung
            summary.getDefaultCell().setBorder(Rectangle.NO_BORDER);

            summary.addCell(noBorderCell("Tổng cộng:", boldFont, Element.ALIGN_LEFT));
            summary.addCell(noBorderCell(formatCurrency(total), boldFont, Element.ALIGN_RIGHT));

            if (order.getDiscountAmount() != null && order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                summary.addCell(noBorderCell("Giảm giá:", infoFont, Element.ALIGN_LEFT));
                summary.addCell(noBorderCell("-" + formatCurrency(order.getDiscountAmount()), infoFont, Element.ALIGN_RIGHT));
            }

            if (payment.getPointDiscount() != null && payment.getPointDiscount().compareTo(BigDecimal.ZERO) > 0) {
                summary.addCell(noBorderCell("Giảm từ điểm:", infoFont, Element.ALIGN_LEFT));
                summary.addCell(noBorderCell("-" + formatCurrency(payment.getPointDiscount()), infoFont, Element.ALIGN_RIGHT));
            }

            summary.addCell(noBorderCell("Thành tiền phải trả:", boldFont, Element.ALIGN_LEFT));
            summary.addCell(noBorderCell(formatCurrency(order.getFinalAmount()), boldFont, Element.ALIGN_RIGHT));

            if (payment.getCashReceived() != null) {
                summary.addCell(noBorderCell("Tiền khách đưa:", boldFont, Element.ALIGN_LEFT));
                summary.addCell(noBorderCell(formatCurrency(payment.getCashReceived()), boldFont, Element.ALIGN_RIGHT));

                summary.addCell(noBorderCell("Tiền thối lại:", boldFont, Element.ALIGN_LEFT));
                summary.addCell(noBorderCell(formatCurrency(payment.getChangeAmount()), boldFont, Element.ALIGN_RIGHT));
            }

            document.add(summary);
            // Người lập hóa đơn
            document.add(Chunk.NEWLINE);
            String createdBy = SecurityContextHolder.getContext().getAuthentication().getName();
            Paragraph signer = new Paragraph("Người lập hóa đơn: " + createdBy, infoFont);
            signer.setAlignment(Element.ALIGN_RIGHT);
            signer.setSpacingBefore(10);
            signer.setSpacingAfter(25);
            document.add(signer);

            // Lời cảm ơn
            Paragraph thanks = new Paragraph("Cảm ơn quý khách đã mua hàng!\nHẹn gặp lại!", boldFont);
            thanks.setAlignment(Element.ALIGN_CENTER);
            thanks.setSpacingBefore(20);
            document.add(thanks);

            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            throw new AppException(ErrorCode.ERROR_CREATING_ORDER_PDF);
        }
    }

// ------------------------ Helper methods ------------------------

    private void addStyledTableHeader(PdfPTable table, String... headers) {
        Font headFont = new Font(Font.HELVETICA, 12, Font.BOLD);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headFont));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBackgroundColor(Color.LIGHT_GRAY);
            cell.setPadding(6);
            table.addCell(cell);
        }
    }
    private PdfPCell noBorderCell(String content, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(content, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4);
        return cell;
    }
    private PdfPCell createCell(String content, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(content, font));
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        return cell;
    }

    private String formatCurrency(BigDecimal amount) {
        return String.format("%,.0f VNĐ", amount);
    }




}
