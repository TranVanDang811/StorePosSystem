package com.possystem.backend.supplier.controller;

import com.possystem.backend.common.response.ApiResponse;
import jakarta.validation.Valid;
import com.possystem.backend.supplier.dto.SupplierRequest;
import com.possystem.backend.supplier.dto.SupplierResponse;
import com.possystem.backend.supplier.dto.SupplierSummaryResponse;
import com.possystem.backend.supplier.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/suppliers")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Supplier", description = "Supplier management")
public class SupplierController {
    SupplierService supplierService;

    @Operation(summary = "Create a supplier", description = "Add a new supplier to the system")
    @PostMapping
    ApiResponse<SupplierResponse> create(@RequestBody @Valid SupplierRequest request) {
        return ApiResponse.<SupplierResponse>builder()
                .result(supplierService.create(request))
                .build();
    }

    @Operation(summary = "Get supplier summary KPIs", description = "Get overview KPIs of suppliers")
    @GetMapping("/summary")
    ApiResponse<SupplierSummaryResponse> getSummary() {
        return ApiResponse.<SupplierSummaryResponse>builder()
                .result(supplierService.getSummary())
                .build();
    }

    @Operation(summary = "Get supplier list", description = "Returns a list of all suppliers (with pagination and optional search)")
    @GetMapping
    ApiResponse<Page<SupplierResponse>> getAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.<Page<SupplierResponse>>builder()
                .result(supplierService.getAll(page - 1, size, keyword))
                .build();
    }

    @Operation(summary = "Search for suppliers by name", description = "Find supplier by keyword in name, phone, email (with pagination)")
    @GetMapping("/search")
    ApiResponse<Page<SupplierResponse>> searchByName(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.<Page<SupplierResponse>>builder()
                .result(supplierService.searchByName(keyword, page - 1, size))
                .build();
    }

    @Operation(summary = "Get supplier details", description = "Get details of a supplier by ID")
    @GetMapping("/{id}")
    ApiResponse<SupplierResponse> getById(@PathVariable String id) {
        return ApiResponse.<SupplierResponse>builder()
                .result(supplierService.getById(id))
                .build();
    }

    @Operation(summary = "Supplier Update", description = "Update supplier information by ID")
    @PutMapping("/{id}")
    ApiResponse<SupplierResponse> update(
            @PathVariable String id,
            @RequestBody @Valid SupplierRequest request) {
        return ApiResponse.<SupplierResponse>builder()
                .result(supplierService.update(id, request))
                .build();
    }

    @Operation(summary = "Delete supplier", description = "Delete a supplier by ID")
    @DeleteMapping("/{id}")
    ApiResponse<Void> delete(@PathVariable String id) {
        supplierService.delete(id);
        return ApiResponse.<Void>builder().message("Xóa nhà cung cấp thành công").build();
    }
}
