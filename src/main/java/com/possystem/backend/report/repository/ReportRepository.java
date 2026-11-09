package com.possystem.backend.report.repository;

import com.possystem.backend.payment.entity.Payment;
import com.possystem.backend.report.dto.ProductSalesReportResponse;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportRepository extends JpaRepository<Payment, String> {

    @Query("""
        SELECT 
            p.productCode,
            p.name,
            COALESCE(c.name, 'Chưa phân loại'),
            SUM(od.quantity),
            SUM(od.totalPrice)
        FROM Payment pay
        JOIN pay.order o
        JOIN o.orderDetails od
        JOIN od.product p
        LEFT JOIN p.category c
        WHERE pay.status = com.possystem.backend.common.enums.PaymentStatus.SUCCESS
          AND (:fromDate IS NULL OR pay.paymentDate >= :fromDate)
          AND (:toDate IS NULL OR pay.paymentDate <= :toDate)
          AND (:keyword IS NULL OR 
               LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:categoryName IS NULL OR c.name = :categoryName)
        GROUP BY p.id, p.productCode, p.name, c.name
        ORDER BY SUM(od.quantity) DESC
    """)
    List<Object[]> getProductSalesReport(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("keyword") String keyword,
            @Param("categoryName") String categoryName
    );
}