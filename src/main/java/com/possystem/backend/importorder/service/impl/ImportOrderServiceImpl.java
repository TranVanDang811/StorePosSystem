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
        try {
            // Get supplier
            Supplier supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new AppException(ErrorCode.SUPPLIER_NOT_FOUND, request.getSupplierId()));

            // Mapping request -> entity
            ImportOrder order = importOrderMapper.toEntity(request);
            order.setSupplier(supplier);

            List<ImportOrderDetail> details = new ArrayList<>();
            BigDecimal totalPrice = BigDecimal.ZERO;
            int totalQuantity = 0;

            // Create a detailed list
            for (ImportOrderDetailRequest d : request.getImportDetails()) {
                Product product = productRepository.findById(d.getProductId())
                        .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND, d.getProductId()));
                if (!product.getSupplier().getId().equals(request.getSupplierId())) {
                    throw new AppException(ErrorCode.PRODUCT_NOT_BELONG_TO_SUPPLIER,
                            product.getName() + " Not owned by this supplier");
                }
                ImportOrderDetail detail = new ImportOrderDetail();
                if (request.getImportDetails() == null || request.getImportDetails().isEmpty()) {
                    throw new IllegalArgumentException("Import order must have at least one product.");
                }
                detail.setImportOrder(order);
                detail.setProduct(product);
                detail.setQuantity(d.getQuantity());
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
            throw new IllegalStateException("Only DRAFT orders can be updated.");
        }

        // Update note
        order.setNote(request.getNote());
        order.setImportUpdateDate(LocalDateTime.now());

        // Recalculate total amount and total quantity
        BigDecimal totalPrice = BigDecimal.ZERO;
        int totalQuantity = 0;

        // Create a map to quickly look up details by productId
        Map<String, ImportOrderDetail> detailMap = order.getImportDetails().stream()
                .collect(Collectors.toMap(d -> d.getProduct().getId(), d -> d));

        // Iterate through the list in the request to update each row
        for (ImportOrderDetailRequest d : request.getImportDetails()) {
            ImportOrderDetail detail = detailMap.get(d.getProductId());
            if (detail != null) {
                // Available -> updated
                detail.setQuantity(d.getQuantity());
                detail.setImportPrice(d.getImportPrice());
            } else {
                // None -> add new
                Product product = productRepository.findById(d.getProductId())
                        .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND, d.getProductId()));

                ImportOrderDetail newDetail = new ImportOrderDetail();
                newDetail.setImportOrder(order);
                newDetail.setProduct(product);
                newDetail.setQuantity(d.getQuantity());
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
        // After updating, recalculate the total.
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

        long totalOrders = ((Number) result[0]).longValue();
        long totalProducts = ((Number) result[1]).longValue();
        BigDecimal totalAmount = (BigDecimal) result[2];

        return new ImportOrderStatisticResponse(totalOrders, totalProducts, totalAmount);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public ImportOrderResponse confirmAndFinalize(String orderId) {

        ImportOrder order = importOrderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.IMPORT_ORDER_NOT_FOUND));

        // Không cho xử lý nếu đã hoàn tất
        if (order.getStatus() == ImportStatus.IMPORTED) {
            throw new IllegalStateException("Order already fully imported.");
        }

        // 1️⃣ Set confirmStatus nếu chưa confirm
        if (order.getConfirmStatus() == ConfirmStatus.UNCONFIRMED) {
            order.setConfirmStatus(ConfirmStatus.CONFIRMED);
        }

        int totalReceived = order.getTotalReceivedQuantity();

        if (totalReceived == 0) {
            throw new IllegalStateException("No quantity has been entered.");
        }

        // 2️⃣ Cộng stock
        for (ImportOrderDetail detail : order.getImportDetails()) {

            int received = detail.getReceivedQuantity();
            int confirmed = detail.getConfirmedQuantity();

            int delta = received - confirmed;

            if (delta > 0) {

                Product product = detail.getProduct();

                int newStock = product.getStock() + delta;
                product.setStock(newStock);


                if (product.getStatus() != ProductStatus.DISCONTINUED
                        && newStock > 0
                        && product.getStatus() == ProductStatus.OUT_OF_STOCK) {

                    product.setStatus(ProductStatus.ACTIVE);
                }

                detail.setConfirmedQuantity(received);
            }
        }

        // 3️⃣ Update ImportStatus
        if (totalReceived >= order.getTotalQuantity()) {
            order.setStatus(ImportStatus.IMPORTED);
        } else {
            order.setStatus(ImportStatus.PARTIALLY_IMPORTED);
        }

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
        if (status != null && !status.isEmpty()) {
            importStatus = ImportStatus.valueOf(status.toUpperCase());
        }

        LocalDateTime fromDateTime = null;
        LocalDateTime toDateTime = null;

        if (fromDate != null) {
            fromDateTime = fromDate.atStartOfDay();
        }

        if (toDate != null) {
            toDateTime = toDate.atTime(23, 59, 59);
        }

        Sort sort = Sort.by("importDate");
        sort = "DESC".equalsIgnoreCase(sortByImportDate)
                ? sort.descending()
                : sort.ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ImportOrder> ordersPage;

        // filter: status + supplier + date
        if (importStatus != null
                && supplierName != null && !supplierName.isEmpty()
                && fromDateTime != null && toDateTime != null) {

            ordersPage = importOrderRepository
                    .findByStatusAndSupplier_NameContainingIgnoreCaseAndImportDateBetween(
                            importStatus, supplierName, fromDateTime, toDateTime, pageable);

        }
        // filter: date
        else if (fromDateTime != null && toDateTime != null) {

            ordersPage = importOrderRepository
                    .findByImportDateBetween(fromDateTime, toDateTime, pageable);

        }
        // filter: status + supplier
        else if (importStatus != null
                && supplierName != null && !supplierName.isEmpty()) {

            ordersPage = importOrderRepository
                    .findByStatusAndSupplier_NameContainingIgnoreCase(
                            importStatus, supplierName, pageable);

        }
        // filter: status
        else if (importStatus != null) {

            ordersPage = importOrderRepository
                    .findByStatus(importStatus, pageable);

        }
        // filter: supplier
        else if (supplierName != null && !supplierName.isEmpty()) {

            ordersPage = importOrderRepository
                    .findBySupplier_NameContainingIgnoreCase(supplierName, pageable);

        }
        // no filter
        else {

            ordersPage = importOrderRepository.findAll(pageable);
        }

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

        if (order.getStatus() == ImportStatus.IMPORTED) {
            throw new IllegalStateException("Cannot delete imported orders");
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
    //--------------
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public ByteArrayInputStream exportImportOrdersToExcel() {
        List<ImportOrder> orders = importOrderRepository.findAll();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Import Orders");

            // Header
            Row header = sheet.createRow(0);
            String[] columns = {"Order ID", "Supplier Name", "Import Date", "Status", "Total Amount"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
            }

            // Data
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            int rowIdx = 1;
            for (ImportOrder order : orders) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(order.getId());
                row.createCell(1).setCellValue(order.getSupplier().getName());
                row.createCell(2).setCellValue(order.getImportDate().format(formatter));
                row.createCell(3).setCellValue(order.getStatus().name());
                row.createCell(4).setCellValue(order.getTotalPrice().doubleValue());
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // ImportOrderService
    @Transactional
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public ImportOrderResponse receiveImportOrderDetail(
            String orderId,
            String detailId,
            int receivedQuantity) {

        ImportOrder order = importOrderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.IMPORT_ORDER_NOT_FOUND));

        if (order.getStatus() == ImportStatus.IMPORTED) {
            throw new IllegalStateException("The order has been processed and received into the warehouse.");
        }


        ImportOrderDetail detail = order.getImportDetails().stream()
                .filter(d -> d.getId().equals(detailId))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.IMPORT_ORDER_DETAIL_NOT_FOUND));

        if (receivedQuantity < 0) {
            throw new IllegalArgumentException("The number received must not be negative.");
        }
        if (receivedQuantity > detail.getQuantity()) {
            throw new IllegalArgumentException("The number received exceeded the number ordered.");
        }

        detail.setReceivedQuantity(receivedQuantity);

        ImportOrder saved = importOrderRepository.save(order);

        return importOrderMapper.toResponse(saved);
    }


}
