package com.possystem.backend.payment.repository;

import com.possystem.backend.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

}


