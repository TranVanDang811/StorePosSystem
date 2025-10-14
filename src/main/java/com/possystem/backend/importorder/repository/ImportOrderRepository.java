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
public interface ImportOrderRepository extends JpaRepository<ImportOrder,String> {
    Page<ImportOrder> findByStatus(ImportStatus status, Pageable pageable);

    Page<ImportOrder> findByStatusAndSupplier_NameContainingIgnoreCase(ImportStatus status, String supplierName, Pageable pageable);

    Page<ImportOrder> findBySupplier_NameContainingIgnoreCase(String supplierName, Pageable pageable);


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


}
