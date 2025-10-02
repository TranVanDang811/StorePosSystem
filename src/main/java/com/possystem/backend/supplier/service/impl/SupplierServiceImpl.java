package com.possystem.backend.supplier.service.impl;

import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.util.mapper.SupplierMapper;
import com.possystem.backend.supplier.dto.SupplierRequest;
import com.possystem.backend.supplier.dto.SupplierResponse;
import com.possystem.backend.supplier.entity.Supplier;
import com.possystem.backend.supplier.repository.SupplierRepository;
import com.possystem.backend.supplier.service.SupplierService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SupplierServiceImpl implements SupplierService {
    SupplierRepository supplierRepository;
    SupplierMapper supplierMapper;

    @PreAuthorize("hasRole('ADMIN')")
    public SupplierResponse create(SupplierRequest request) {
        if (isSupplierNameExists(request.getName())) {
            throw new AppException(ErrorCode.SUPPLIER_NAME_ALREADY_EXISTS);
        }
        Supplier supplier = supplierMapper.toEntity(request);
        supplier = supplierRepository.save(supplier);
        return supplierMapper.toResponse(supplier);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Page<SupplierResponse> getAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return supplierRepository.findAll(pageable)
                .map(supplierMapper::toResponse);
    }


    @PreAuthorize("hasRole('ADMIN')")
    public Page<SupplierResponse> searchByName(String keyword, int page, int size) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new AppException(ErrorCode.NAME_SUPPLIER_NOT_EXISTS);
        }
        Pageable pageable = PageRequest.of(page, size);
        return supplierRepository.findByNameContainingIgnoreCase(keyword, pageable)
                .map(supplierMapper::toResponse);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SupplierResponse getById(String id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SUPPLIER_NOT_FOUND));
        return supplierMapper.toResponse(supplier);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SupplierResponse update(String id, SupplierRequest request) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SUPPLIER_NOT_FOUND));

        if (!supplier.getName().equalsIgnoreCase(request.getName()) &&
                isSupplierNameExists(request.getName())) {
            throw new AppException(ErrorCode.SUPPLIER_NAME_ALREADY_EXISTS);
        }

        supplierMapper.updateSupplierFromDto(request, supplier);
        supplier = supplierRepository.save(supplier);
        return supplierMapper.toResponse(supplier);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void delete(String id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SUPPLIER_NOT_FOUND));
        supplierRepository.delete(supplier);
    }

    public boolean isSupplierNameExists(String name) {
        return supplierRepository.existsByNameIgnoreCase(name);
    }
}
