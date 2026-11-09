package com.possystem.backend.importorder.repository;

import com.possystem.backend.importorder.entity.ImportOrderDetail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImportOrderDetailRepository
        extends JpaRepository<ImportOrderDetail, String> {

    Page<ImportOrderDetail> findByImportOrderId(String orderId, Pageable pageable);

    Optional<ImportOrderDetail> findByIdAndImportOrderId(String detailId, String orderId);

    boolean existsByProductId(String productId);
}
