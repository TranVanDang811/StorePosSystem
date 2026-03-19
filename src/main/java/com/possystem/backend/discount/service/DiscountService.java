package com.possystem.backend.discount.service;

import com.possystem.backend.discount.dto.DiscountRequest;
import com.possystem.backend.discount.dto.DiscountResponse;
import com.possystem.backend.discount.dto.PointDiscountResult;
import com.possystem.backend.order.entity.Orders;
import com.possystem.backend.user.entity.CustomerProfile;

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
    BigDecimal applyDiscountCode(Orders order, String discountCode);
    PointDiscountResult applyPointDiscount(CustomerProfile profile,
                                           Integer requestUsedPoints,
                                           BigDecimal orderAmount);

}
