package com.possystem.backend.importorder.controller;

import com.possystem.backend.common.enums.ImportStatus;
import com.possystem.backend.common.response.ApiResponse;
import com.possystem.backend.importorder.dto.*;
import com.possystem.backend.importorder.service.ImportOrderService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/import-orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Import Orders", description = "APIs for managing import orders (warehouse receipts)")
public class ImportOrderController {
    ImportOrderService importOrderService;

    // Create new receipt
    @Operation(summary = "Create new import order", description = "Create a new import order with detailed product items.")
    @PostMapping
    ApiResponse<ImportOrderResponse> createImportOrder(@RequestBody ImportOrderCreateRequest request) {
        return ApiResponse.<ImportOrderResponse>builder()
                .result(importOrderService.createImportOrder(request))
                .build();
    }
    @Operation(summary = "Update import order", description = "Update import order details (only allowed if status is DRAFT).")
    @PutMapping("/{orderId}")
    ApiResponse<ImportOrderResponse> updateImportOrder(
            @PathVariable String orderId,
            @RequestBody @Valid ImportOrderUpdateRequest request) {
        return ApiResponse.<ImportOrderResponse>builder()
                .result(importOrderService.updateImportOrder(orderId, request))
                .build();
    }

    @Operation(summary = "Get import statistics", description = "Get total import amount and quantities within a specific date range.")
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE')")
    ApiResponse<ImportOrderStatisticResponse> getImportStatistics(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.<ImportOrderStatisticResponse>builder()
                .result(importOrderService.getStatistics(from, to))
                .build();
    }

    @PutMapping("/{id}/confirm-finalize")
    public ApiResponse<ImportOrderResponse> confirmAndFinalize(
            @PathVariable String id) {

        return ApiResponse.<ImportOrderResponse>builder()
                .result(importOrderService.confirmAndFinalize(id))
                .message("Import order confirmed and finalized successfully")
                .build();
    }


    @Operation(summary = "Get list of import orders", description = "Retrieve paginated list of import orders with optional filters by supplier name, status, and date sorting.")
    @GetMapping
    public ApiResponse<Page<ImportOrderResponse>> getImportOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) ImportStatus status,
            @RequestParam(required = false) String sortByImportDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate
    ) {

        Page<ImportOrderResponse> orders = importOrderService.getImportOrders(
                status != null ? status.name() : null,
                sortByImportDate,
                supplierName,
                fromDate,
                toDate,
                page - 1,
                size
        );

        return ApiResponse.<Page<ImportOrderResponse>>builder()
                .result(orders)
                .build();
    }
    @Operation(summary = "Cập nhật số lượng thực nhận cho một dòng chi tiết")
    @PatchMapping("/{orderId}/receive-detail/{detailId}")
    ApiResponse<ImportOrderResponse> updateReceivedQuantity(
            @PathVariable String orderId,
            @PathVariable String detailId,
            @RequestParam int receivedQuantity) {

        return ApiResponse.success(
                importOrderService.receiveImportOrderDetail(orderId, detailId, receivedQuantity)
        );
    }

    @GetMapping("/{orderId}/details")
    @Operation(summary = "Get import order details with pagination")
    public ApiResponse<Page<ImportOrderDetailResponse>> getDetails(
            @PathVariable String orderId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size) {

        return ApiResponse.<Page<ImportOrderDetailResponse>>builder()
                .result(
                        importOrderService.getImportOrderDetails(
                                orderId,
                                page - 1,
                                size
                        )
                )
                .build();
    }

    @GetMapping("/{orderId}/details/{detailId}")
    public ApiResponse<ImportOrderDetailResponse> getDetail(
            @PathVariable String orderId,
            @PathVariable String detailId) {

        return ApiResponse.success(
                importOrderService.getImportOrderDetail(orderId, detailId)
        );
    }
    //--------------
    @Operation(summary = "Export import orders to Excel", description = "Export all import orders into an Excel (.xlsx) file.")
    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> exportImportOrdersToExcel() {
        ByteArrayInputStream in = importOrderService.exportImportOrdersToExcel();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=import-orders.xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    @Operation(summary = "Get import order by ID", description = "Retrieve detailed information about a specific import order.")
    // Get entry details by ID
    @GetMapping("/{id}")
    public ApiResponse<ImportOrderResponse> getById(@PathVariable String id) {
        return ApiResponse.<ImportOrderResponse>builder()
                .result(importOrderService.getById(id))
                .build();
    }

    @Operation(summary = "Delete import order", description = "Delete an import order (only allowed if it has not been confirmed).")
    @DeleteMapping("/{orderId}")
    public ApiResponse<Void> deleteImportOrder(@PathVariable String orderId) {
        importOrderService.deleteImportOrder(orderId);
        return ApiResponse.<Void>builder()
                .message("Delete import order successfully")
                .build();
    }



}
