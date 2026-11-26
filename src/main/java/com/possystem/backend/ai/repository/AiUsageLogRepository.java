package com.possystem.backend.ai.repository;

import com.possystem.backend.ai.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, String> {

    @Query("""
        SELECT COALESCE(SUM(u.totalTokens), 0)
        FROM AiUsageLog u
        WHERE u.userId = :userId AND u.createdAt >= :since
    """)
    long sumTotalTokensByUserIdSince(@Param("userId") String userId, @Param("since") LocalDateTime since);

    @Query("""
        SELECT COALESCE(SUM(u.costEstimateUsd), 0)
        FROM AiUsageLog u
        WHERE u.userId = :userId AND u.createdAt >= :since
    """)
    BigDecimal sumCostByUserIdSince(@Param("userId") String userId, @Param("since") LocalDateTime since);

    @Query("""
        SELECT COALESCE(SUM(u.totalTokens), 0)
        FROM AiUsageLog u
        WHERE u.createdAt >= :since
    """)
    long sumTotalTokensGlobalSince(@Param("since") LocalDateTime since);

    @Query("""
        SELECT COALESCE(SUM(u.costEstimateUsd), 0)
        FROM AiUsageLog u
        WHERE u.createdAt >= :since
    """)
    BigDecimal sumCostGlobalSince(@Param("since") LocalDateTime since);
}
