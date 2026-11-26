package com.possystem.backend.common.outbox.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "daily_sales_projection", uniqueConstraints = {
        @UniqueConstraint(name = "uq_daily_projection_date", columnNames = {"metric_date"})
})
public class DailySalesProjection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(name = "metric_date", nullable = false)
    LocalDate metricDate;

    @Column(name = "orders_created", nullable = false)
    @Builder.Default
    long ordersCreated = 0L;

    @Column(name = "orders_completed", nullable = false)
    @Builder.Default
    long ordersCompleted = 0L;

    @Column(name = "orders_cancelled", nullable = false)
    @Builder.Default
    long ordersCancelled = 0L;

    @Column(name = "payments_completed", nullable = false)
    @Builder.Default
    long paymentsCompleted = 0L;

    @Column(name = "payments_failed", nullable = false)
    @Builder.Default
    long paymentsFailed = 0L;

    @Column(name = "total_revenue", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "total_discount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    BigDecimal totalDiscount = BigDecimal.ZERO;

    @Column(name = "total_loyalty_points_earned", nullable = false)
    @Builder.Default
    long totalLoyaltyPointsEarned = 0L;

    @Column(name = "vouchers_redeemed", nullable = false)
    @Builder.Default
    long vouchersRedeemed = 0L;

    @Column(name = "shifts_closed", nullable = false)
    @Builder.Default
    long shiftsClosed = 0L;

    @Column(name = "updated_at", nullable = false)
    LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }
}
