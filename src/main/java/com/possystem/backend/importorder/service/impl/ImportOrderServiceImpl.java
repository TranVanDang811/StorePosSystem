package com.possystem.backend.importorder.service.impl;

import com.possystem.backend.common.enums.ConfirmStatus;
import com.possystem.backend.common.enums.ImportStatus;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.util.mapper.ImportOrderMapper;
import com.possystem.backend.importorder.dto.*;
import com.possystem.backend.importorder.entity.ImportOrder;
import com.possystem.backend.importorder.entity.ImportOrderDetail;
import com.possystem.backend.importorder.repository.ImportOrderDetailRepository;
import com.possystem.backend.importorder.repository.ImportOrderRepository;
import com.possystem.backend.importorder.service.ImportOrderService;
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.supplier.entity.Supplier;
import com.possystem.backend.supplier.repository.SupplierRepository;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ImportOrderServiceImpl implements ImportOrderService {
    SupplierRepository supplierRepository;
    ProductRepository productRepository;
    ImportOrderRepository importOrderRepository;
    ImportOrderMapper importOrderMapper;
    ImportOrderDetailRepository importOrderDetailRepository;

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    @Transactional
    public ImportOrderResponse createImportOrder(ImportOrderCreateRequest request) {
        if (request.getImportDetails() == null || request.getImportDetails().isEmpty()) {
            throw new IllegalArgumentException("Đơn nhập hàng phải có ít nhất một sản phẩm.");
        }

        try {
            Supplier supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new AppException(ErrorCode.SUPPLIER_NOT_FOUND, request.getSupplierId()));

            ImportOrder order = importOrderMapper.toEntity(request);
            order.setSupplier(supplier);
            order.setImportDate(LocalDateTime.now());
            order.setImportUpdateDate(LocalDateTime.now());

            List<ImportOrderDetail> details = new ArrayList<>();
            BigDecimal totalPrice = BigDecimal.ZERO;
            int totalQuantity = 0;

            for (ImportOrderDetailRequest d : request.getImportDetails()) {
                Product product = productRepository.findById(d.getProductId())
                        .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND, d.getProductId()));

                if (product.getSupplier() == null || !product.getSupplier().getId().equals(request.getSupplierId())) {
                    throw new AppException(ErrorCode.PRODUCT_NOT_BELONG_TO_SUPPLIER,
                            product.getName() + " không thuộc nhà cung cấp này");
                }

                ImportOrderDetail detail = new ImportOrderDetail();
                detail.setImportOrder(order);
                detail.setProduct(product);
                detail.setQuantity(d.getQuantity());
                detail.setReceivedQuantity(0);
                detail.setConfirmedQuantity(0);
                detail.setImportPrice(d.getImportPrice());

                totalQuantity += d.getQuantity();
                totalPrice = totalPrice.add(d.getImportPrice().multiply(BigDecimal.valueOf(d.getQuantity())));

                details.add(detail);
            }

            order.setImportDetails(details);
            order.setTotalPrice(totalPrice);
            order.setTotalQuantity(totalQuantity);
            order.setStatus(ImportStatus.DRAFT);
            order.setConfirmStatus(ConfirmStatus.UNCONFIRMED);

            ImportOrder savedOrder = importOrderRepository.save(order);

            ImportOrder fullOrder = importOrderRepository.findByIdWithDetails(savedOrder.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

            return importOrderMapper.toResponse(fullOrder);

        } catch (Exception e) {
            log.error("Unexpected error while creating import order", e);
            throw e;
        }
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    @Transactional
    public ImportOrderResponse updateImportOrder(String orderId, ImportOrderUpdateRequest request) {
        ImportOrder order = importOrderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.IMPORT_ORDER_NOT_FOUND, orderId));

        if (order.getStatus() != ImportStatus.DRAFT) {
            throw new IllegalStateException("Chỉ được sửa đơn nhập hàng ở trạng thái DRAFT.");
        }

        order.setNote(request.getNote());
        order.setImportUpdateDate(LocalDateTime.now());

        BigDecimal totalPrice = BigDecimal.ZERO;
        int totalQuantity = 0;

        Map<String, ImportOrderDetail> detailMap = order.getImportDetails().stream()
                .collect(Collectors.toMap(d -> d.getProduct().getId(), d -> d));

        for (ImportOrderDetailRequest d : request.getImportDetails()) {
            ImportOrderDetail detail = detailMap.get(d.getProductId());
            if (detail != null) {
                detail.setQuantity(d.getQuantity());
                detail.setImportPrice(d.getImportPrice());
            } else {
                Product product = productRepository.findById(d.getProductId())
                        .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND, d.getProductId()));

                ImportOrderDetail newDetail = new ImportOrderDetail();
                newDetail.setImportOrder(order);
                newDetail.setProduct(product);
                newDetail.setQuantity(d.getQuantity());
                newDetail.setReceivedQuantity(0);
                newDetail.setConfirmedQuantity(0);
                newDetail.setImportPrice(d.getImportPrice());
                order.getImportDetails().add(newDetail);
            }
        }

        List<String> requestProductIds = request.getImportDetails()
                .stream()
                .map(ImportOrderDetailRequest::getProductId)
                .toList();

        order.getImportDetails().removeIf(detail ->
                !requestProductIds.contains(detail.getProduct().getId())
        );

        for (ImportOrderDetail detail : order.getImportDetails()) {
            BigDecimal lineTotal = detail.getImportPrice().multiply(BigDecimal.valueOf(detail.getQuantity()));
            totalPrice = totalPrice.add(lineTotal);
            totalQuantity += detail.getQuantity();
        }

        order.setTotalPrice(totalPrice);
        order.setTotalQuantity(totalQuantity);

        ImportOrder saved = importOrderRepository.save(order);
        return importOrderMapper.toResponse(saved);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public ImportOrderStatisticResponse getStatistics(LocalDate from, LocalDate to) {
        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        Object[] result = (Object[]) importOrderRepository.getStatisticsBetween(fromDateTime, toDateTime);

        long totalOrders = result[0] != null ? ((Number) result[0]).longValue() : 0L;
        long totalProducts = result[1] != null ? ((Number) result[1]).longValue() : 0L;
        BigDecimal totalAmount = result[2] != null ? (BigDecimal) result[2] : BigDecimal.ZERO;

        return new ImportOrderStatisticResponse(totalOrders, totalProducts, totalAmount);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public ImportOrderResponse confirmAndFinalize(String orderId) {
        ImportOrder order = importOrderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.IMPORT_ORDER_NOT_FOUND));

        if (order.getStatus() == ImportStatus.IMPORTED) {
            throw new IllegalStateException("Đơn nhập hàng này đã hoàn thành nhập kho.");
        }
        if (order.getStatus() == ImportStatus.CANCELLED) {
            throw new IllegalStateException("Đơn nhập hàng này đã bị hủy, không thể nhập kho.");
        }

        boolean isOverdue = order.isOverdue();
        int totalReceived = order.getTotalReceivedQuantity();

        // Quy định: Nếu đơn hàng tính từ ngày đặt đến ngày nhận quá 3 ngày:
        // - Nếu chưa nhận được gì (totalReceived == 0): Nhập là 0, coi như có đơn nhưng NCC không giao, vẫn cho xác nhận bình thường để chốt/đóng đơn.
        if (totalReceived == 0) {
            if (!isOverdue) {
                throw new IllegalStateException("Chưa có số lượng thực nhận nào được ghi nhận và đơn hàng chưa quá hạn 3 ngày.");
            }
            String overdueNote = "[Quá hạn " + order.getDaysSinceOrder() + " ngày - NCC không giao hàng]";
            if (order.getNote() == null || order.getNote().isBlank()) {
                order.setNote(overdueNote);
            } else if (!order.getNote().contains("NCC không giao hàng")) {
                order.setNote(order.getNote() + " " + overdueNote);
            }
            order.setConfirmStatus(ConfirmStatus.CONFIRMED);
            order.setStatus(ImportStatus.IMPORTED);
            order.setImportUpdateDate(LocalDateTime.now());
            return importOrderMapper.toResponse(importOrderRepository.save(order));
        }

        // Cập nhật tồn kho và giá vốn bình quân gia quyền cho từng sản phẩm
        for (ImportOrderDetail detail : order.getImportDetails()) {
            int received = detail.getReceivedQuantity();
            int confirmed = detail.getConfirmedQuantity();
            int delta = received - confirmed;

            if (delta > 0) {
                Product product = detail.getProduct();
                int oldStock = product.getStock();
                int newStock = oldStock + delta;
                product.setStock(newStock);

                // Tính giá vốn bình quân gia quyền (Moving Average Cost)
                // ((Tồn cũ * Giá vốn cũ) + (Nhập mới * Giá nhập mới)) / Tổng tồn mới
                BigDecimal oldCost = product.getCost() != null ? product.getCost() : BigDecimal.ZERO;
                BigDecimal importPrice = detail.getImportPrice() != null ? detail.getImportPrice() : BigDecimal.ZERO;

                if (newStock > 0) {
                    BigDecimal oldTotalValue = oldCost.multiply(BigDecimal.valueOf(Math.max(0, oldStock)));
                    BigDecimal newImportValue = importPrice.multiply(BigDecimal.valueOf(delta));
                    BigDecimal newAverageCost = oldTotalValue.add(newImportValue)
                            .divide(BigDecimal.valueOf(newStock), 2, RoundingMode.HALF_UP);
                    product.setCost(newAverageCost);
                } else {
                    product.setCost(importPrice);
                }

                // Cập nhật trạng thái OUT_OF_STOCK -> ACTIVE khi có hàng
                if (product.getStatus() != ProductStatus.DISCONTINUED
                        && newStock > 0
                        && product.getStatus() == ProductStatus.OUT_OF_STOCK) {
                    product.setStatus(ProductStatus.ACTIVE);
                }

                detail.setConfirmedQuantity(received);
                productRepository.save(product);
            }
        }

        order.setConfirmStatus(ConfirmStatus.CONFIRMED);

        if (isOverdue && totalReceived < order.getTotalQuantity()) {
            String overdueNote = "[Quá hạn " + order.getDaysSinceOrder() + " ngày - Chốt nhận một phần, phần thiếu NCC không giao]";
            if (order.getNote() == null || order.getNote().isBlank()) {
                order.setNote(overdueNote);
            } else if (!order.getNote().contains("Quá hạn")) {
                order.setNote(order.getNote() + " " + overdueNote);
            }
            order.setStatus(ImportStatus.IMPORTED);
        } else if (totalReceived >= order.getTotalQuantity()) {
            order.setStatus(ImportStatus.IMPORTED);
        } else {
            order.setStatus(ImportStatus.PARTIALLY_IMPORTED);
        }

        order.setImportUpdateDate(LocalDateTime.now());
        return importOrderMapper.toResponse(importOrderRepository.save(order));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public ImportOrderResponse cancelImportOrder(String orderId) {
        ImportOrder order = importOrderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.IMPORT_ORDER_NOT_FOUND));

        if (order.getStatus() == ImportStatus.IMPORTED) {
            throw new IllegalStateException("Không thể hủy đơn hàng đã hoàn thành nhập kho.");
        }
        if (order.getStatus() == ImportStatus.PARTIALLY_IMPORTED) {
            throw new IllegalStateException("Đơn hàng đã nhập kho một phần, không thể hủy hoàn toàn.");
        }

        order.setStatus(ImportStatus.CANCELLED);
        order.setImportUpdateDate(LocalDateTime.now());
        return importOrderMapper.toResponse(importOrderRepository.save(order));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public Page<ImportOrderResponse> getImportOrders(
            String status,
            String sortByImportDate,
            String supplierName,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size) {

        ImportStatus importStatus = null;
        if (status != null && !status.trim().isEmpty()) {
            try {
                importStatus = ImportStatus.fromString(status);
            } catch (Exception ignored) {}
        }

        LocalDateTime fromDateTime = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime toDateTime = toDate != null ? toDate.atTime(23, 59, 59) : null;

        Sort sort = Sort.by("importDate");
        sort = "DESC".equalsIgnoreCase(sortByImportDate) ? sort.descending() : sort.ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ImportOrder> ordersPage = importOrderRepository.filterImportOrders(
                importStatus,
                supplierName != null && !supplierName.trim().isEmpty() ? supplierName.trim() : null,
                fromDateTime,
                toDateTime,
                pageable
        );

        return ordersPage.map(importOrderMapper::toResponse);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public ImportOrderResponse getById(String id) {
        ImportOrder order = importOrderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.IMPORT_ORDER_NOT_FOUND));
        return importOrderMapper.toResponse(order);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    @Transactional
    public void deleteImportOrder(String orderId) {
        ImportOrder order = importOrderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.IMPORT_ORDER_NOT_FOUND));

        if (order.getStatus() == ImportStatus.IMPORTED || order.getStatus() == ImportStatus.PARTIALLY_IMPORTED) {
            throw new IllegalStateException("Không thể xóa đơn đã nhập kho.");
        }

        importOrderRepository.delete(order);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public Page<ImportOrderDetailResponse> getImportOrderDetails(
            String orderId,
            int page,
            int size) {

        Pageable pageable = PageRequest.of(page, size);

        if (!importOrderRepository.existsById(orderId)) {
            throw new AppException(ErrorCode.IMPORT_ORDER_NOT_FOUND);
        }

        return importOrderDetailRepository
                .findByImportOrderId(orderId, pageable)
                .map(importOrderMapper::toDetailResponse);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public ImportOrderDetailResponse getImportOrderDetail(
            String orderId,
            String detailId) {

        ImportOrderDetail detail =
                importOrderDetailRepository
                        .findByIdAndImportOrderId(detailId, orderId)
                        .orElseThrow(() ->
                                new AppException(ErrorCode.IMPORT_ORDER_DETAIL_NOT_FOUND));

        return importOrderMapper.toDetailResponse(detail);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public ByteArrayInputStream exportImportOrdersToExcel() {
        List<ImportOrder> orders = importOrderRepository.findAll();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Import Orders");

            Row header = sheet.createRow(0);
            String[] columns = {"Mã phiếu", "Nhà cung cấp", "Ngày tạo", "Trạng thái", "Tổng số lượng", "Tổng tiền (VNĐ)"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            int rowIdx = 1;
            for (ImportOrder order : orders) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(order.getId());
                row.createCell(1).setCellValue(order.getSupplier() != null ? order.getSupplier().getName() : "");
                row.createCell(2).setCellValue(order.getImportDate() != null ? order.getImportDate().format(formatter) : "");
                row.createCell(3).setCellValue(order.getStatus() != null ? order.getStatus().name() : "");
                row.createCell(4).setCellValue(order.getTotalQuantity());
                row.createCell(5).setCellValue(order.getTotalPrice() != null ? order.getTotalPrice().doubleValue() : 0.0);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public ImportOrderResponse receiveImportOrderDetail(
            String orderId,
            String detailId,
            int receivedQuantity) {

        ImportOrder order = importOrderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.IMPORT_ORDER_NOT_FOUND));

        if (order.getStatus() == ImportStatus.IMPORTED) {
            throw new IllegalStateException("Đơn nhập hàng đã hoàn thành, không thể thay đổi số lượng thực nhận.");
        }
        if (order.getStatus() == ImportStatus.CANCELLED) {
            throw new IllegalStateException("Đơn nhập hàng đã bị hủy.");
        }

        ImportOrderDetail detail = order.getImportDetails().stream()
                .filter(d -> d.getId().equals(detailId))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.IMPORT_ORDER_DETAIL_NOT_FOUND));

        if (receivedQuantity < 0) {
            throw new IllegalArgumentException("Số lượng nhận không được là số âm.");
        }
        if (receivedQuantity > detail.getQuantity()) {
            throw new IllegalArgumentException("Số lượng nhận vượt quá số lượng đặt hàng.");
        }

        detail.setReceivedQuantity(receivedQuantity);

        // Chuyển trạng thái sang RECEIVING nếu đang là DRAFT
        if (order.getStatus() == ImportStatus.DRAFT) {
            order.setStatus(ImportStatus.RECEIVING);
        }

        order.setImportUpdateDate(LocalDateTime.now());
        ImportOrder saved = importOrderRepository.save(order);

        return importOrderMapper.toResponse(saved);
    }
}
