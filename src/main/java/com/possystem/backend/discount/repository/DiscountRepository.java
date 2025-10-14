package com.possystem.backend.discount.repository;

import com.possystem.backend.discount.entity.Discount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiscountRepository extends JpaRepository<Discount, String> {
    Optional<Discount> findByCode(String code);

    @Query("SELECT d FROM Discount d WHERE d.endDate >= :cutoff OR d.active = true")
    List<Discount> findAllActiveOrRecentlyExpired(@Param("cutoff") LocalDateTime cutoff);
}
