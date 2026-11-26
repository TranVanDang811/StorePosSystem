package com.possystem.backend.product.repository;

import com.possystem.backend.product.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, String> {
    Page<StockMovement> findByProduct_IdOrderByCreatedAtDesc(String productId, Pageable pageable);
    List<StockMovement> findByReferenceId(String referenceId);
}
