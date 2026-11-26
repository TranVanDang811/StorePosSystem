package com.possystem.backend.common.outbox.repository;

import com.possystem.backend.common.outbox.entity.DailySalesProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DailySalesProjectionRepository extends JpaRepository<DailySalesProjection, String> {
    Optional<DailySalesProjection> findByMetricDate(LocalDate metricDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DailySalesProjection d WHERE d.metricDate = :date")
    Optional<DailySalesProjection> findByMetricDateWithLock(@Param("date") LocalDate date);
}
