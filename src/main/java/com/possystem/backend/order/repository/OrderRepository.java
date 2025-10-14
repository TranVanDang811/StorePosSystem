package com.possystem.backend.order.repository;

import com.possystem.backend.order.entity.Orders;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;


@Repository
public interface OrderRepository extends JpaRepository<Orders,String > {
    List<Orders> findAllByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
