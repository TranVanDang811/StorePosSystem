package com.possystem.backend.common.outbox.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "invoice_records", uniqueConstraints = {
        @UniqueConstraint(name = "uq_invoice_payment", columnNames = {"payment_id"}),
        @UniqueConstraint(name = "uq_invoice_number", columnNames = {"invoice_number"})
}, indexes = {
        @Index(name = "idx_invoice_order", columnList = "order_id")
})
public class InvoiceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(name = "invoice_number", nullable = false, length = 64)
    String invoiceNumber;

    @Column(name = "order_id", nullable = false, length = 64)
    String orderId;

    @Column(name = "order_code", length = 64)
    String orderCode;

    @Column(name = "payment_id", nullable = false, length = 64)
    String paymentId;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    BigDecimal totalAmount;

    @Column(name = "payment_method", length = 32)
    String paymentMethod;

    @Column(nullable = false, length = 32)
    @Builder.Default
    String status = "GENERATED";

    @Column(name = "rendered_html", columnDefinition = "LONGTEXT", nullable = false)
    String renderedHtml;

    @Column(name = "generated_at", nullable = false)
    LocalDateTime generatedAt;
}
