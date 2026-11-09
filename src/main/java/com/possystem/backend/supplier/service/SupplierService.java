package com.possystem.backend.supplier.service;

import com.possystem.backend.supplier.dto.SupplierRequest;
import com.possystem.backend.supplier.dto.SupplierResponse;
import com.possystem.backend.supplier.dto.SupplierSummaryResponse;
import org.springframework.data.domain.Page;

public interface SupplierService {
    SupplierResponse create(SupplierRequest request);
    Page<SupplierResponse> getAll(int page, int size, String keyword);
    Page<SupplierResponse> searchByName(String keyword, int page, int size);
    SupplierResponse getById(String id);
    SupplierResponse update(String id, SupplierRequest request);
    SupplierSummaryResponse getSummary();
    void delete(String id);
}
