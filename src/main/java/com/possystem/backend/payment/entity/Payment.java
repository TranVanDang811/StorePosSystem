package com.possystem.backend.payment.entity;

import com.possystem.backend.common.entity.AbstractEntity;
import com.possystem.backend.common.enums.PaymentMethod;
import com.possystem.backend.common.enums.PaymentStatus;
import com.possystem.backend.order.entity.Orders;
import com.possystem.backend.user.entity.User;
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
public class Payment extends AbstractEntity {
        @ManyToOne
    @JoinColumn(name = "user_id", nullable = true)
        User user;
    @Column(nullable = false)
    BigDecimal amount; // Tổng tiền thanh toán
    @Column(precision = 15, scale = 2)
    BigDecimal pointDiscount;
    Integer earnedPoints;
    Integer usedPoints = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    PaymentMethod method; // CASH, CREDIT_CARD, MOMO...

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    PaymentStatus status; // PENDING, SUCCESS, FAILED, REFUNDED

    @Column(nullable = false)
    LocalDateTime paymentDate; // Thời gian thanh toán

    String transactionId; // Mã giao dịch (cổng thanh toán)

    String note; // Ghi chú

    @Column(precision = 15, scale = 2)
    BigDecimal cashReceived;  // Số tiền khách đưa

    @Column(precision = 15, scale = 2)
    BigDecimal changeAmount;  // Số tiền thối lại

    BigDecimal totalRefunded; // tổng tiền đã hoàn

    @Column(columnDefinition = "TEXT")
    String refundHistoryJson; // JSON lưu danh sách các lần hoàn tiền

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    Orders order;
}
