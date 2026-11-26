package com.possystem.backend.discount.repository;

import com.possystem.backend.discount.entity.DiscountUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiscountUsageRepository extends JpaRepository<DiscountUsage, String> {
    boolean existsByDiscount_IdAndOrder_Id(String discountId, String orderId);
    List<DiscountUsage> findByUserPhone(String userPhone);
}
