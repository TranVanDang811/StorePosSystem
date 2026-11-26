package com.possystem.backend.user.repository;

import com.possystem.backend.user.entity.LoyaltyTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, String> {
    Page<LoyaltyTransaction> findByCustomerProfile_IdOrderByCreatedAtDesc(String customerProfileId, Pageable pageable);
    Optional<LoyaltyTransaction> findByIdempotencyKey(String idempotencyKey);
    boolean existsByIdempotencyKey(String idempotencyKey);
}
