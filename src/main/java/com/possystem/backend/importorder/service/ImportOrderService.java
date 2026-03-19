package com.possystem.backend.importorder.service;

import com.possystem.backend.importorder.dto.*;
import org.springframework.data.domain.Page;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;


public interface ImportOrderService {
    ImportOrderResponse createImportOrder(ImportOrderCreateRequest request);
    ImportOrderResponse updateImportOrder(String orderId, ImportOrderUpdateRequest request);
    ImportOrderStatisticResponse getStatistics(LocalDate from, LocalDate to);
    ImportOrderResponse confirmAndFinalize(String orderId);
    Page<ImportOrderResponse> getImportOrders(
            String status,
            String sortByImportDate,
            String supplierName,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size);
    ImportOrderResponse getById(String id);
    void deleteImportOrder(String orderId);
    ByteArrayInputStream exportImportOrdersToExcel();
    ImportOrderResponse receiveImportOrderDetail(
            String orderId,
            String detailId,
            int receivedQuantity);

    Page<ImportOrderDetailResponse> getImportOrderDetails(
            String orderId,
            int page,
            int size);
    ImportOrderDetailResponse getImportOrderDetail(
            String orderId,
            String detailId);
}
