package com.possystem.backend.entity;

import com.possystem.backend.user.entity.User;
import com.possystem.backend.common.enums.OrderStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class Order extends AbstractEntity{
    @ManyToOne
    private Customer customer;

    @ManyToOne
    private User staff;

    @ManyToOne
    private Discount discount; // voucher

    private BigDecimal totalAmount;
    private BigDecimal discountAmount;       // từ voucher
    private BigDecimal discountFromPoints;   // từ điểm
    private BigDecimal finalAmount;

    private Integer usedPoints = 0;          // điểm đã dùng

    @Enumerated(EnumType.STRING)
    private OrderStatus status;
}
