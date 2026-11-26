package com.possystem.backend.discount.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class Discount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;
    String name;
    String code;// mã giảm giá
    String description;

    @Column(nullable = false)
    BigDecimal value;

    boolean active;

    LocalDateTime startDate;

    LocalDateTime endDate;

    @Column(name = "usage_limit", nullable = false)
    int usageLimit = 0; // 0 = không giới hạn

    @Column(name = "used_count", nullable = false)
    int usedCount = 0;

    public boolean isCurrentlyValid() {
        LocalDateTime now = LocalDateTime.now();
        return (now.isAfter(startDate) || now.isEqual(startDate))
                && (now.isBefore(endDate) || now.isEqual(endDate))
                && active
                && !hasReachedUsageLimit();
    }

    public boolean hasReachedUsageLimit() {
        return usageLimit > 0 && usedCount >= usageLimit;
    }

    public void incrementUsedCount() {
        if (hasReachedUsageLimit()) {
            throw new com.possystem.backend.common.exception.AppException(
                    com.possystem.backend.common.exception.ErrorCode.COUPON_INVALID,
                    "Mã giảm giá đã đạt giới hạn số lượt sử dụng tối đa");
        }
        this.usedCount++;
    }
}
