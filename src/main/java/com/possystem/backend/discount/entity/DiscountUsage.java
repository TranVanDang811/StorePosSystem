package com.possystem.backend.discount.entity;

import com.possystem.backend.order.entity.Orders;
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
@Table(name = "discount_usage", uniqueConstraints = {
        @UniqueConstraint(name = "uq_discount_order", columnNames = {"discount_id", "order_id"})
}, indexes = {
        @Index(name = "idx_discount_usage_phone", columnList = "user_phone")
})
public class DiscountUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_id", nullable = false)
    Discount discount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    Orders order;

    @Column(name = "user_phone", length = 50)
    String userPhone;

    @Column(name = "discount_amount", precision = 15, scale = 2, nullable = false)
    BigDecimal discountAmount;

    @Column(name = "used_at", nullable = false)
    LocalDateTime usedAt;
}
