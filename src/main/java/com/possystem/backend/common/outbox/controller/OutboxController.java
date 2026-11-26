package com.possystem.backend.common.outbox.controller;

import com.possystem.backend.common.outbox.entity.AuditEvent;
import com.possystem.backend.common.outbox.entity.DailySalesProjection;
import com.possystem.backend.common.outbox.entity.InvoiceRecord;
import com.possystem.backend.common.outbox.entity.OutboxEvent;
import com.possystem.backend.common.outbox.repository.AuditEventRepository;
import com.possystem.backend.common.outbox.repository.DailySalesProjectionRepository;
import com.possystem.backend.common.outbox.repository.InvoiceRecordRepository;
import com.possystem.backend.common.outbox.service.OutboxProcessor;
import com.possystem.backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/outbox")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Transactional Outbox Management", description = "Giám sát hàng đợi Event-Driven Outbox, DLQ, Event Replay, Audit & Projections")
public class OutboxController {

    OutboxProcessor outboxProcessor;
    AuditEventRepository auditEventRepository;
    InvoiceRecordRepository invoiceRecordRepository;
    DailySalesProjectionRepository dailySalesProjectionRepository;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    @Operation(summary = "Lấy thống kê hàng đợi sự kiện Outbox (Pending, Processing, Processed, Failed, Dead-Letter)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(200)
                .result(outboxProcessor.getOutboxStats())
                .build());
    }

    @GetMapping("/events")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    @Operation(summary = "Truy vấn danh sách Outbox Events theo trạng thái, loại sự kiện hoặc aggregateId")
    public ResponseEntity<ApiResponse<Page<OutboxEvent>>> getEvents(
            @RequestParam(required = false) OutboxEvent.OutboxStatus status,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String aggregateId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.<Page<OutboxEvent>>builder()
                .code(200)
                .result(outboxProcessor.listEvents(status, eventType, aggregateId, PageRequest.of(Math.max(0, page), Math.min(size, 100))))
                .build());
    }

    @GetMapping("/dlq")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    @Operation(summary = "Xem danh sách các sự kiện đang nằm trong Dead Letter Queue (DLQ)")
    public ResponseEntity<ApiResponse<Page<OutboxEvent>>> getDeadLetters(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.<Page<OutboxEvent>>builder()
                .code(200)
                .result(outboxProcessor.listEvents(OutboxEvent.OutboxStatus.DEAD_LETTER, null, null, PageRequest.of(Math.max(0, page), Math.min(size, 100))))
                .build());
    }

    @PostMapping("/dlq/retry")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Kích hoạt xử lý lại toàn bộ sự kiện trong Dead-Letter Queue (DLQ)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> retryDeadLetters() {
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(200)
                .message("Đã hoàn tất khôi phục các sự kiện Dead-Letter vào hàng đợi chờ xử lý.")
                .result(outboxProcessor.retryDeadLetters())
                .build());
    }

    @PostMapping("/events/{eventId}/replay")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Phát lại (Replay) một sự kiện cụ thể theo ID an toàn nhờ Idempotent Consumer")
    public ResponseEntity<ApiResponse<OutboxEvent>> replayEvent(@PathVariable String eventId) {
        return ResponseEntity.ok(ApiResponse.<OutboxEvent>builder()
                .code(200)
                .message("Đã đưa sự kiện vào hàng đợi replay.")
                .result(outboxProcessor.replayEvent(eventId))
                .build());
    }

    @PostMapping("/recover-stale")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Thu hồi các lock PROCESSING bị treo do worker crash")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recoverStaleLocks() {
        int recovered = outboxProcessor.recoverStaleLocks();
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(200)
                .result(Map.of("recoveredCount", recovered))
                .build());
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    @Operation(summary = "Xem lịch sử Audit Event phát sinh bất đồng bộ từ Outbox")
    public ResponseEntity<ApiResponse<Page<AuditEvent>>> getAuditEvents(
            @RequestParam(required = false) String aggregateId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(size, 100));
        Page<AuditEvent> result = (aggregateId != null && !aggregateId.isBlank())
                ? auditEventRepository.findByAggregateIdOrderByOccurredAtDesc(aggregateId.trim(), pageable)
                : auditEventRepository.findAllByOrderByOccurredAtDesc(pageable);
        return ResponseEntity.ok(ApiResponse.<Page<AuditEvent>>builder()
                .code(200)
                .result(result)
                .build());
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    @Operation(summary = "Xem danh sách hóa đơn điện tử được sinh bất đồng bộ")
    public ResponseEntity<ApiResponse<Page<InvoiceRecord>>> getAsyncInvoices(
            @RequestParam(required = false) String orderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(size, 100));
        Page<InvoiceRecord> result = (orderId != null && !orderId.isBlank())
                ? invoiceRecordRepository.findByOrderIdOrderByGeneratedAtDesc(orderId.trim(), pageable)
                : invoiceRecordRepository.findAllByOrderByGeneratedAtDesc(pageable);
        return ResponseEntity.ok(ApiResponse.<Page<InvoiceRecord>>builder()
                .code(200)
                .result(result)
                .build());
    }

    @GetMapping("/analytics/daily")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    @Operation(summary = "Xem projection thống kê bán hàng ngày được cập nhật bất đồng bộ")
    public ResponseEntity<ApiResponse<DailySalesProjection>> getDailyProjection(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        DailySalesProjection projection = dailySalesProjectionRepository.findByMetricDate(target)
                .orElse(DailySalesProjection.builder().metricDate(target).build());
        return ResponseEntity.ok(ApiResponse.<DailySalesProjection>builder()
                .code(200)
                .result(projection)
                .build());
    }
}
