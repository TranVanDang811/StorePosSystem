package com.possystem.backend.discount.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class PointDiscountResult {

    private BigDecimal discountAmount;
    private int usedPoints;

}