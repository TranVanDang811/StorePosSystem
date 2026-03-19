package com.possystem.backend.shift.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)

public class ShiftClosingReportResponse {

     String closedById;
     String closedByName;
     String openedByName;
    String shiftCode;
     LocalDateTime startTime;
     LocalDateTime endTime;

     BigDecimal openingCash;
     BigDecimal cashSales;
     BigDecimal cashRefunds;
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

     long totalOrdersPaid;
     long totalCustomers;
}