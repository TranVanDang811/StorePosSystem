package com.possystem.backend.shift.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CloseShiftRequest {

    long note500k;
    long note200k;
    long note100k;
    long note50k;
    long note20k;
    long note10k;
    long note5k;
    long note2k;
    long note1k;

}