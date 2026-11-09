package com.possystem.backend.importorder.repository;

import com.possystem.backend.common.enums.ImportStatus;
import com.possystem.backend.importorder.entity.ImportOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ImportOrderRepository extends JpaRepository<ImportOrder, String> {

    @Query("""
        SELECT io FROM ImportOrder io
        WHERE (:status IS NULL OR io.status = :status)
          AND (:supplierName IS NULL OR :supplierName = '' OR LOWER(io.supplier.name) LIKE LOWER(CONCAT('%', :supplierName, '%')))
          AND (:from IS NULL OR io.importDate >= :from)
          AND (:to IS NULL OR io.importDate <= :to)
    """)
    Page<ImportOrder> filterImportOrders(
            @Param("status") ImportStatus status,
            @Param("supplierName") String supplierName,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    Page<ImportOrder> findByStatus(ImportStatus status, Pageable pageable);
    long countBySupplier_Id(String supplierId);

    @Query("SELECT io.supplier.id, COUNT(io) FROM ImportOrder io WHERE io.supplier IS NOT NULL GROUP BY io.supplier.id")
    java.util.List<Object[]> countImportOrdersGroupedBySupplier();

    Page<ImportOrder> findByStatusAndSupplier_NameContainingIgnoreCase(ImportStatus status, String supplierName, Pageable pageable);

    Page<ImportOrder> findBySupplier_NameContainingIgnoreCase(String supplierName, Pageable pageable);

    Page<ImportOrder> findByImportDateBetweenAndStatus(
            LocalDateTime from,
            LocalDateTime to,
            ImportStatus status,
            Pageable pageable
    );

    Page<ImportOrder> findByImportDateBetween(
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    );

    Page<ImportOrder> findByStatusAndSupplier_NameContainingIgnoreCaseAndImportDateBetween(
            ImportStatus status,
            String supplierName,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "importDetails")
    @Query("SELECT o FROM ImportOrder o WHERE o.id = :id")
    Optional<ImportOrder> findByIdWithDetails(@Param("id") String id);

    @Query("""
    SELECT COUNT(io.id), 
           COALESCE(SUM(d.quantity), 0), 
           COALESCE(SUM(d.quantity * d.importPrice), 0)
    FROM ImportOrder io
    JOIN io.importDetails d
    WHERE io.importDate BETWEEN :from AND :to
      AND io.status = 'IMPORTED'
    """)
    Object getStatisticsBetween(@Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);

    @Query("""
        SELECT COUNT(io) FROM ImportOrder io
        WHERE io.status IN (
            com.possystem.backend.common.enums.ImportStatus.DRAFT,
            com.possystem.backend.common.enums.ImportStatus.RECEIVING,
            com.possystem.backend.common.enums.ImportStatus.PARTIALLY_IMPORTED
        )
    """)
    long countPendingImportOrders();

    @Query("""
        SELECT COUNT(io) FROM ImportOrder io
        WHERE io.status IN (
            com.possystem.backend.common.enums.ImportStatus.DRAFT,
            com.possystem.backend.common.enums.ImportStatus.RECEIVING,
            com.possystem.backend.common.enums.ImportStatus.PARTIALLY_IMPORTED
        )
        AND io.importDate < :cutoff
    """)
    long countOverdueImportOrders(@Param("cutoff") LocalDateTime cutoff);
}
