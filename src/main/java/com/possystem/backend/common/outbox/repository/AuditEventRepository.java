package com.possystem.backend.common.outbox.repository;

import com.possystem.backend.common.outbox.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {
    boolean existsByEventId(String eventId);
    Optional<AuditEvent> findByEventId(String eventId);
    Page<AuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);
    Page<AuditEvent> findByAggregateIdOrderByOccurredAtDesc(String aggregateId, Pageable pageable);
}
