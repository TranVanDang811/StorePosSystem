package com.possystem.backend.payment.repository;

import com.possystem.backend.payment.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, String> {
    Optional<WebhookEvent> findByIdempotencyKey(String idempotencyKey);
    boolean existsByIdempotencyKey(String idempotencyKey);
    List<WebhookEvent> findAllByStatusAndCreatedAtBefore(WebhookEvent.WebhookStatus status, LocalDateTime before);
}
