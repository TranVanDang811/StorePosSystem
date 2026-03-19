package com.possystem.backend.order.repository;

import com.possystem.backend.common.enums.OrderStatus;
import com.possystem.backend.order.entity.Orders;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;


@Repository
public interface OrderRepository extends JpaRepository<Orders,String > {
    List<Orders> findAllByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // 🔹 Lấy danh sách đơn hàng trong khoảng thời gian
    List<Orders> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("""
            SELECT p.name AS productName, SUM(od.quantity) AS totalQuantity, SUM(od.totalPrice) AS totalRevenue
            FROM Orders o
            JOIN o.orderDetails od
            JOIN od.product p
            WHERE DATE(o.createdAt) = :date
            GROUP BY p.name
            ORDER BY totalQuantity DESC
            """)
    List<Object[]> findTopSellingProducts(@Param("date") LocalDate date);


    @Query("""
            SELECT p.name, SUM(od.quantity) AS totalQuantity, SUM(od.totalPrice) AS totalRevenue
            FROM OrderDetail od
            JOIN od.product p
            JOIN od.order o
            WHERE FUNCTION('MONTH', o.createdAt) = :#{#month.monthValue}
              AND FUNCTION('YEAR', o.createdAt) = :#{#month.year}
              AND o.status = 'PAID'
            GROUP BY p.name
            ORDER BY totalQuantity DESC
            """)
    List<Object[]> findTopSellingProductsByMonth(@Param("month") YearMonth month);


    Page<Orders> findByStatus(OrderStatus status, Pageable pageable);

    List<Orders> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime time);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);


    List<Orders> findTop5ByOrderByCreatedAtDesc();

    @Query("""
            SELECT o
            FROM Orders o
            WHERE o.orderCode LIKE :prefix%
            ORDER BY o.orderCode DESC
            """)
    List<Orders> findLastOrderCodeOfDay(@Param("prefix") String prefix, Pageable pageable);

}