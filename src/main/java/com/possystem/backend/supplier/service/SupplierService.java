package com.possystem.backend.supplier.service;

import com.possystem.backend.supplier.dto.SupplierRequest;
import com.possystem.backend.supplier.dto.SupplierResponse;
import org.springframework.data.domain.Page;

public interface SupplierService {
    SupplierResponse create(SupplierRequest request);
    Page<SupplierResponse> getAll(int page, int size);
    Page<SupplierResponse> searchByName(String keyword, int page, int size);
    void delete(String id);
    SupplierResponse update(String id, SupplierRequest request);
    SupplierResponse getById(String id);
}
