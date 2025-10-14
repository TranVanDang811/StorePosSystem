package com.possystem.backend.discount.service;

import com.possystem.backend.discount.dto.DiscountRequest;
import com.possystem.backend.discount.dto.DiscountResponse;
import com.possystem.backend.order.entity.Orders;

import java.math.BigDecimal;
import java.util.List;

public interface DiscountService {
    DiscountResponse createDiscount(DiscountRequest request);
    DiscountResponse updateDiscount(String id, DiscountRequest request);
    void deleteDiscount(String id);
    List<DiscountResponse> getAllDiscounts();
    List<DiscountResponse> getActiveDiscounts();
    DiscountResponse getByCode(String code);
    DiscountResponse getDiscountById(String id);
    void updateDiscountStatusAutomatically();
    BigDecimal applyDiscount(Orders order, String discountCode, Integer usedPoints);
}
