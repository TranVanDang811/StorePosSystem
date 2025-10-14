package com.possystem.backend.importorder.service;

import com.possystem.backend.importorder.dto.ImportOrderCreateRequest;
import com.possystem.backend.importorder.dto.ImportOrderResponse;
import com.possystem.backend.importorder.dto.ImportOrderStatisticResponse;
import com.possystem.backend.importorder.dto.ImportOrderUpdateRequest;
import org.springframework.data.domain.Page;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;

public interface ImportOrderService {
    ImportOrderResponse createImportOrder(ImportOrderCreateRequest request);
    ImportOrderResponse updateImportOrder(String orderId, ImportOrderUpdateRequest request);
    ImportOrderStatisticResponse getStatistics(LocalDate from, LocalDate to);
    void confirmImportOrder(String orderId);
    Page<ImportOrderResponse> getImportOrders(
            String status,
            String sortByImportDate,
            String supplierName,
            int page,
            int size);
    ImportOrderResponse getById(String id);
    void deleteImportOrder(String orderId);
    ByteArrayInputStream exportImportOrdersToExcel();
}
