package com.possystem.backend.importorder.repository;

import com.possystem.backend.importorder.entity.ImportOrderDetail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImportOrderDetailRepository
        extends JpaRepository<ImportOrderDetail, String> {

    Page<ImportOrderDetail> findByImportOrderId(String orderId, Pageable pageable);

    Optional<ImportOrderDetail> findByIdAndImportOrderId(String detailId, String orderId);
}