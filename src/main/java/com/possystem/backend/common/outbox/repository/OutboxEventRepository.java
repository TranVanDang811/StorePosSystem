package com.possystem.backend.common.outbox.repository;

import com.possystem.backend.common.outbox.entity.OutboxEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    List<OutboxEvent> findAllByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status, Pageable pageable);

    long countByStatus(OutboxEvent.OutboxStatus status);

    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE e.status = com.possystem.backend.common.outbox.entity.OutboxEvent$OutboxStatus.PENDING
               OR (e.status = com.possystem.backend.common.outbox.entity.OutboxEvent$OutboxStatus.FAILED
                   AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now))
            ORDER BY e.createdAt ASC
            """)
    List<OutboxEvent> findDueEvents(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE e.status = com.possystem.backend.common.outbox.entity.OutboxEvent$OutboxStatus.PROCESSING
              AND e.lockedAt < :cutoff
            ORDER BY e.lockedAt ASC
            """)
    List<OutboxEvent> findStaleProcessingEvents(@Param("cutoff") LocalDateTime cutoff);

    @Query("""
            SELECT COUNT(e) > 0 FROM OutboxEvent e
            WHERE e.orderingKey = :orderingKey
              AND e.id <> :currentId
              AND e.createdAt < :createdAt
              AND e.status IN :blockingStatuses
            """)
    boolean existsEarlierBlockingEvent(
            @Param("orderingKey") String orderingKey,
            @Param("currentId") String currentId,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("blockingStatuses") Collection<OutboxEvent.OutboxStatus> blockingStatuses);

    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE (:status IS NULL OR e.status = :status)
              AND (:eventType IS NULL OR e.eventType = :eventType)
              AND (:aggregateId IS NULL OR e.aggregateId = :aggregateId)
            ORDER BY e.createdAt DESC
            """)
    Page<OutboxEvent> findFiltered(
            @Param("status") OutboxEvent.OutboxStatus status,
            @Param("eventType") String eventType,
            @Param("aggregateId") String aggregateId,
            Pageable pageable);
}
