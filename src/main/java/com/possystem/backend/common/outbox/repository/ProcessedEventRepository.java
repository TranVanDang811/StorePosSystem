package com.possystem.backend.common.outbox.repository;

import com.possystem.backend.common.outbox.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
    boolean existsByEventIdAndConsumerName(String eventId, String consumerName);
    List<ProcessedEvent> findByEventId(String eventId);
    long countByConsumerName(String consumerName);
}
