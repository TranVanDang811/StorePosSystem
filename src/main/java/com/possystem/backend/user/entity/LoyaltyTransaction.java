package com.possystem.backend.user.entity;

import com.possystem.backend.common.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "loyalty_transaction", indexes = {
        @Index(name = "idx_loyalty_tx_profile", columnList = "customer_profile_id, created_at"),
        @Index(name = "idx_loyalty_tx_ref", columnList = "reference_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_loyalty_idempotency", columnNames = {"idempotency_key"})
})
public class LoyaltyTransaction extends AbstractEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_profile_id", nullable = false)
    CustomerProfile customerProfile;

    @Column(name = "reference_id", length = 100)
    String referenceId; // paymentId

    @Column(name = "transaction_type", nullable = false, length = 50)
    String transactionType; // EARN, REDEEM, REFUND, ADJUSTMENT

    @Column(name = "points_change", nullable = false)
    int pointsChange;

    @Column(name = "balance_after", nullable = false)
    int balanceAfter;

    @Column(name = "idempotency_key", length = 100)
    String idempotencyKey;

    @Column(length = 255)
    String note;
}
