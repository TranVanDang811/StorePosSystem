package com.possystem.backend.importorder.service.impl;

import com.possystem.backend.common.cache.CacheInvalidationService;
import com.possystem.backend.common.enums.ConfirmStatus;
import com.possystem.backend.common.enums.ImportStatus;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.observability.PosBusinessMetrics;
import com.possystem.backend.common.outbox.event.PosEventType;
import com.possystem.backend.common.outbox.service.OutboxPublisher;
import com.possystem.backend.common.util.mapper.ImportOrderMapper;
import com.possystem.backend.importorder.dto.*;
import com.possystem.backend.importorder.entity.ImportOrder;
import com.possystem.backend.importorder.entity.ImportOrderDetail;
import com.possystem.backend.importorder.repository.ImportOrderDetailRepository;
import com.possystem.backend.importorder.repository.ImportOrderRepository;
import com.possystem.backend.importorder.service.ImportOrderService;
import com.possystem.backend.importorder.util.ImportOrderExcelExporter;
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.product.entity.StockMovement;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.product.repository.StockMovementRepository;
import com.possystem.backend.supplier.entity.Supplier;
import com.possystem.backend.supplier.repository.SupplierRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
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
    StockMovementRepository stockMovementRepository;
    CacheInvalidationService cacheInvalidationService;
    OutboxPublisher outboxPublisher;
    PosBusinessMetrics businessMetrics;

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

            List<String> productIds = request.getImportDetails().stream()
                    .map(ImportOrderDetailRequest::getProductId)
                    .distinct()
                    .toList();
            Map<String, Product> productMap = productRepository.findAllById(productIds).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p));

            for (ImportOrderDetailRequest d : request.getImportDetails()) {
                Product product = productMap.get(d.getProductId());
                if (product == null) {
                    product = productRepository.findById(d.getProductId()).orElse(null);
                }
                if (product == null) {
                    throw new AppException(ErrorCode.PRODUCT_NOT_FOUND, d.getProductId());
                }

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

            if (cacheInvalidationService != null) {
                cacheInvalidationService.evictReportCaches();
            }
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

        List<String> missingProductIds = request.getImportDetails().stream()
                .map(ImportOrderDetailRequest::getProductId)
                .filter(id -> !detailMap.containsKey(id))
                .distinct()
                .toList();

        Map<String, Product> missingProductMap = missingProductIds.isEmpty() ? Map.of()
                : productRepository.findAllById(missingProductIds).stream()
                        .collect(Collectors.toMap(Product::getId, p -> p));

        for (ImportOrderDetailRequest d : request.getImportDetails()) {
            ImportOrderDetail detail = detailMap.get(d.getProductId());
            if (detail != null) {
                detail.setQuantity(d.getQuantity());
                detail.setImportPrice(d.getImportPrice());
            } else {
                Product product = missingProductMap.get(d.getProductId());
                if (product == null) {
                    product = productRepository.findById(d.getProductId()).orElse(null);
                }
                if (product == null) {
                    throw new AppException(ErrorCode.PRODUCT_NOT_FOUND, d.getProductId());
                }

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

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    @Transactional
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
            ImportOrderResponse res = importOrderMapper.toResponse(importOrderRepository.save(order));
            if (cacheInvalidationService != null) {
                cacheInvalidationService.evictAfterImportFinalized();
            }
            return res;
        }

        // Cập nhật tồn kho và giá vốn bình quân gia quyền qua domain method trên Product
        for (ImportOrderDetail detail : order.getImportDetails()) {
            int received = detail.getReceivedQuantity();
            int confirmed = detail.getConfirmedQuantity();
            int delta = received - confirmed;

            if (delta > 0) {
                Product product = detail.getProduct();
                int beforeStock = product.getStock();
                product.updateMovingAverageCost(delta, detail.getImportPrice());
                detail.setConfirmedQuantity(received);
                productRepository.save(product);

                if (stockMovementRepository != null) {
                    StockMovement movement = StockMovement.builder()
                            .product(product)
                            .movementType("IMPORT")
                            .referenceId(order.getId())
                            .quantityBefore(beforeStock)
                            .quantityChange(delta)
                            .quantityAfter(product.getStock())
                            .note("Nhập kho hàng từ NCC - Đơn nhập " + order.getId())
                            .build();
                    stockMovementRepository.save(movement);
                }

                if (outboxPublisher != null) {
                    Map<String, Object> invPayload = new java.util.LinkedHashMap<>();
                    invPayload.put("productId", product.getId());
                    invPayload.put("productCode", product.getProductCode());
                    invPayload.put("productName", product.getName());
                    invPayload.put("movementType", "IMPORT");
                    invPayload.put("beforeStock", beforeStock);
                    invPayload.put("quantityChange", delta);
                    invPayload.put("afterStock", product.getStock());
                    invPayload.put("referenceId", order.getId());
                    outboxPublisher.publishInCurrentTransaction(
                            "INVENTORY",
                            product.getId(),
                            PosEventType.INVENTORY_UPDATED,
                            "product:" + product.getId(),
                            order.getId(),
                            invPayload);
                }
                if (businessMetrics != null) {
                    businessMetrics.recordInventoryAdjustment("IMPORT", delta);
                }
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
        ImportOrderResponse res = importOrderMapper.toResponse(importOrderRepository.save(order));
        if (cacheInvalidationService != null) {
            cacheInvalidationService.evictAfterImportFinalized();
        }
        return res;
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
        ImportOrderResponse res = importOrderMapper.toResponse(importOrderRepository.save(order));
        if (cacheInvalidationService != null) {
            cacheInvalidationService.evictAfterImportFinalized();
        }
        return res;
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
        if (cacheInvalidationService != null) {
            cacheInvalidationService.evictReportCaches();
        }
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
        return ImportOrderExcelExporter.exportToExcel(orders);
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
