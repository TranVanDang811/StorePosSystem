package com.possystem.backend.common.outbox.repository;

import com.possystem.backend.common.outbox.entity.InvoiceRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceRecordRepository extends JpaRepository<InvoiceRecord, String> {
    boolean existsByPaymentId(String paymentId);
    Optional<InvoiceRecord> findByPaymentId(String paymentId);
    Page<InvoiceRecord> findByOrderIdOrderByGeneratedAtDesc(String orderId, Pageable pageable);
    Page<InvoiceRecord> findAllByOrderByGeneratedAtDesc(Pageable pageable);
}
