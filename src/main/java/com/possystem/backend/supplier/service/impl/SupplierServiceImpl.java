package com.possystem.backend.supplier.service.impl;

import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.util.mapper.SupplierMapper;
import com.possystem.backend.importorder.repository.ImportOrderRepository;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.supplier.dto.SupplierRequest;
import com.possystem.backend.supplier.dto.SupplierResponse;
import com.possystem.backend.supplier.dto.SupplierSummaryResponse;
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

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SupplierServiceImpl implements SupplierService {
    SupplierRepository supplierRepository;
    SupplierMapper supplierMapper;
    ProductRepository productRepository;
    ImportOrderRepository importOrderRepository;

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public SupplierResponse create(SupplierRequest request) {
        if (request.getName() != null && isSupplierNameExists(request.getName().trim())) {
            throw new AppException(ErrorCode.SUPPLIER_NAME_ALREADY_EXISTS);
        }
        Supplier supplier = supplierMapper.toEntity(request);
        if (supplier.getName() != null) {
            supplier.setName(supplier.getName().trim());
        }
        if (supplier.getPhone() != null) {
            supplier.setPhone(supplier.getPhone().trim());
        }
        if (supplier.getEmail() != null) {
            supplier.setEmail(supplier.getEmail().trim());
        }
        if (supplier.getAddress() != null) {
            supplier.setAddress(supplier.getAddress().trim());
        }
        supplier = supplierRepository.save(supplier);
        SupplierResponse response = supplierMapper.toResponse(supplier);
        response.setProductCount(0L);
        response.setImportOrderCount(0L);
        return response;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE') or hasRole('EMPLOYEE')")
    public Page<SupplierResponse> getAll(int page, int size, String keyword) {
        Pageable pageable = PageRequest.of(page, size);
        String kw = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        Page<Supplier> supplierPage = supplierRepository.filterSuppliers(kw, pageable);

        java.util.Map<String, Long> productCountMap = productRepository.countProductsGroupedBySupplier()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> String.valueOf(row[0]),
                        row -> ((Number) row[1]).longValue(),
                        (v1, v2) -> v1
                ));

        java.util.Map<String, Long> importCountMap = importOrderRepository.countImportOrdersGroupedBySupplier()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> String.valueOf(row[0]),
                        row -> ((Number) row[1]).longValue(),
                        (v1, v2) -> v1
                ));

        return supplierPage.map(supplier -> {
            SupplierResponse res = supplierMapper.toResponse(supplier);
            res.setProductCount(productCountMap.getOrDefault(supplier.getId(), 0L));
            res.setImportOrderCount(importCountMap.getOrDefault(supplier.getId(), 0L));
            return res;
        });
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE') or hasRole('EMPLOYEE')")
    public Page<SupplierResponse> searchByName(String keyword, int page, int size) {
        return getAll(page, size, keyword);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE') or hasRole('EMPLOYEE')")
    public SupplierResponse getById(String id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SUPPLIER_NOT_FOUND));
        SupplierResponse res = supplierMapper.toResponse(supplier);
        res.setProductCount(productRepository.countBySupplier_Id(supplier.getId()));
        res.setImportOrderCount(importOrderRepository.countBySupplier_Id(supplier.getId()));
        return res;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public SupplierResponse update(String id, SupplierRequest request) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SUPPLIER_NOT_FOUND));

        if (request.getName() != null) {
            String newName = request.getName().trim();
            if (!supplier.getName().equalsIgnoreCase(newName)) {
                Optional<Supplier> existing = supplierRepository.findByName(newName);
                if (existing.isPresent() && !existing.get().getId().equals(supplier.getId())) {
                    throw new AppException(ErrorCode.SUPPLIER_NAME_ALREADY_EXISTS);
                }
            }
            supplier.setName(newName);
        }

        if (request.getPhone() != null) {
            supplier.setPhone(request.getPhone().trim());
        }
        if (request.getEmail() != null) {
            supplier.setEmail(request.getEmail().trim());
        }
        if (request.getAddress() != null) {
            supplier.setAddress(request.getAddress().trim());
        }

        Supplier saved = supplierRepository.save(supplier);
        SupplierResponse res = supplierMapper.toResponse(saved);
        res.setProductCount(productRepository.countBySupplier_Id(saved.getId()));
        res.setImportOrderCount(importOrderRepository.countBySupplier_Id(saved.getId()));
        return res;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE') or hasRole('EMPLOYEE')")
    public SupplierSummaryResponse getSummary() {
        long totalSuppliers = supplierRepository.count();

        java.util.Set<String> activeSupplierIds = new java.util.HashSet<>();
        var pGrouped = productRepository.countProductsGroupedBySupplier();
        if (pGrouped != null) {
            for (Object[] row : pGrouped) {
                if (row[0] != null) activeSupplierIds.add(String.valueOf(row[0]));
            }
        }
        var iGrouped = importOrderRepository.countImportOrdersGroupedBySupplier();
        if (iGrouped != null) {
            for (Object[] row : iGrouped) {
                if (row[0] != null) activeSupplierIds.add(String.valueOf(row[0]));
            }
        }

        long activeSuppliers = activeSupplierIds.size();
        long idleSuppliers = Math.max(0, totalSuppliers - activeSuppliers);
        long totalProductsSupplied = productRepository.countBySupplierIsNotNull();
        long totalImportOrders = importOrderRepository.count();

        return SupplierSummaryResponse.builder()
                .totalSuppliers(totalSuppliers)
                .activeSuppliers(activeSuppliers)
                .idleSuppliers(idleSuppliers)
                .totalProductsSupplied(totalProductsSupplied)
                .totalImportOrders(totalImportOrders)
                .build();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public void delete(String id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SUPPLIER_NOT_FOUND));

        long productCount = productRepository.countBySupplier_Id(supplier.getId());
        long importOrderCount = importOrderRepository.countBySupplier_Id(supplier.getId());

        if (productCount > 0 || importOrderCount > 0) {
            throw new AppException(ErrorCode.SUPPLIER_HAS_DEPENDENCIES);
        }

        supplierRepository.delete(supplier);
    }

    public boolean isSupplierNameExists(String name) {
        return supplierRepository.existsByNameIgnoreCase(name);
    }
}
