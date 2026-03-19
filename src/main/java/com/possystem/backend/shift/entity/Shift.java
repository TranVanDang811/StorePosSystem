package com.possystem.backend.shift.entity;

import com.possystem.backend.common.enums.ShiftStatus;
import com.possystem.backend.user.entity.User;
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
@Table(indexes = {
        @Index(name = "idx_shift_status", columnList = "status"),
        @Index(name = "idx_shift_time", columnList = "startTime,endTime")
})
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(unique = true)
    String shiftCode;

    @ManyToOne
    @JoinColumn(name = "opened_by")
    User openedBy;

    @ManyToOne
    @JoinColumn(name = "closed_by")
    User closedBy;

    LocalDateTime startTime;
    LocalDateTime endTime;
    BigDecimal cashSales;
    BigDecimal cashRefunds;
    BigDecimal openingCash;
    BigDecimal expectedCash;
    BigDecimal countedCash;
    BigDecimal difference;
    BigDecimal depositedCash;


    long note500k;
    long note200k;
    long note100k;
    long note50k;
    long note20k;
    long note10k;
    long note5k;
    long note2k;
    long note1k;




    Long totalOrders;
    Long totalCustomers;

    @Column(columnDefinition = "TEXT")
    String note;

    @Enumerated(EnumType.STRING)
    ShiftStatus status;
}
