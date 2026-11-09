package com.possystem.backend.payment.repository;

import com.possystem.backend.common.enums.PaymentMethod;
import com.possystem.backend.common.enums.PaymentStatus;
import com.possystem.backend.payment.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    Payment findByOrder_Id(String orderId);

    List<Payment> findByPaymentDateBetween(LocalDateTime start, LocalDateTime end);

    @Query("""
SELECT COALESCE(SUM(p.amount),0)
FROM Payment p
WHERE p.status = 'SUCCESS'
AND p.paymentDate BETWEEN :start AND :end
""")
    BigDecimal getRevenueBetween(LocalDateTime start, LocalDateTime end);

    @Query("""
SELECT COUNT(DISTINCT p.user.id)
FROM Payment p
WHERE p.user IS NOT NULL
AND p.status = 'SUCCESS'
AND p.paymentDate BETWEEN :start AND :end
""")
    long countMemberCustomersToday(LocalDateTime start, LocalDateTime end);

    @Query("""
SELECT COUNT(p)
FROM Payment p
WHERE p.user IS NULL
AND p.status = 'SUCCESS'
AND p.paymentDate BETWEEN :start AND :end
""")
    long countGuestCustomersToday(LocalDateTime start, LocalDateTime end);

    @Query("""
SELECT COALESCE(SUM(p.amount),0)
FROM Payment p
WHERE p.method = 'CASH'
AND p.status = 'SUCCESS'
AND p.paymentDate BETWEEN :start AND :end
""")
    BigDecimal sumCashByShift(LocalDateTime start, LocalDateTime end);

    @Query("""
SELECT COUNT(p)
FROM Payment p
WHERE p.status = 'SUCCESS'
AND p.paymentDate BETWEEN :start AND :end
""")
    long countPaidOrders(LocalDateTime start, LocalDateTime end);

    @Query("""
SELECT COUNT(DISTINCT p.user.id)
FROM Payment p
WHERE p.user IS NOT NULL
AND p.status = 'SUCCESS'
AND p.paymentDate BETWEEN :start AND :end
""")
    long countCustomers(LocalDateTime start, LocalDateTime end);

    @Query("""
SELECT COALESCE(SUM(p.totalRefunded), 0)
FROM Payment p
WHERE p.method = 'CASH'
AND p.status IN ('SUCCESS', 'REFUNDED')
AND p.paymentDate BETWEEN :start AND :end
""")
    BigDecimal sumCashRefunds(LocalDateTime start, LocalDateTime end);

    @Query("""
SELECT p FROM Payment p
LEFT JOIN p.user u
LEFT JOIN p.order o
WHERE 
(:fromDate IS NULL OR p.paymentDate >= :fromDate)
AND (:toDate IS NULL OR p.paymentDate <= :toDate)
AND (:status IS NULL OR p.status = :status)
AND (:method IS NULL OR p.method = :method)
AND (
    :keyword IS NULL OR
    LOWER(p.id) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
    LOWER(o.id) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
    LOWER(COALESCE(p.transactionId, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
    LOWER(COALESCE(u.fullName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
    LOWER(COALESCE(u.phone, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
)
ORDER BY p.paymentDate DESC
""")
    Page<Payment> filterPayments(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("status") PaymentStatus status,
            @Param("method") PaymentMethod method,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
SELECT 
    COUNT(p),
    SUM(CASE WHEN p.status = com.possystem.backend.common.enums.PaymentStatus.SUCCESS THEN p.amount END),
    SUM(CASE WHEN p.status = com.possystem.backend.common.enums.PaymentStatus.SUCCESS AND p.method = com.possystem.backend.common.enums.PaymentMethod.CASH THEN p.amount END),
    SUM(CASE WHEN p.status = com.possystem.backend.common.enums.PaymentStatus.SUCCESS AND p.method != com.possystem.backend.common.enums.PaymentMethod.CASH THEN p.amount END),
    SUM(p.totalRefunded)
FROM Payment p
LEFT JOIN p.user u
LEFT JOIN p.order o
WHERE 
(:fromDate IS NULL OR p.paymentDate >= :fromDate)
AND (:toDate IS NULL OR p.paymentDate <= :toDate)
AND (:status IS NULL OR p.status = :status)
AND (:method IS NULL OR p.method = :method)
AND (
    :keyword IS NULL OR
    LOWER(p.id) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
    LOWER(o.id) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
    LOWER(COALESCE(p.transactionId, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
    LOWER(COALESCE(u.fullName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
    LOWER(COALESCE(u.phone, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
)
""")
    Object getPaymentSummaryStats(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("status") PaymentStatus status,
            @Param("method") PaymentMethod method,
            @Param("keyword") String keyword
    );

    @Query("""
        SELECT p FROM Payment p
        WHERE p.user.id = :userId
        ORDER BY p.paymentDate DESC
    """)
    Page<Payment> findByUserId(@Param("userId") String userId, Pageable pageable);

    @Query("""
        SELECT COUNT(p), COALESCE(SUM(p.amount), 0.0), MAX(p.paymentDate)
        FROM Payment p
        WHERE p.user.id = :userId AND p.status = com.possystem.backend.common.enums.PaymentStatus.SUCCESS
    """)
    Object getCustomerSpendingStats(@Param("userId") String userId);
}
