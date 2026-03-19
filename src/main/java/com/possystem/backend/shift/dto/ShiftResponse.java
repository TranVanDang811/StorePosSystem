package com.possystem.backend.shift.dto;

import com.possystem.backend.common.enums.ShiftStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ShiftResponse {

    String id;
    String openedByName;
    String closedByName;
    String shiftCode;
    LocalDateTime startTime;

    LocalDateTime endTime;

    BigDecimal openingCash;

    BigDecimal expectedCash;

    BigDecimal countedCash;

    BigDecimal difference;

    ShiftStatus status;

}