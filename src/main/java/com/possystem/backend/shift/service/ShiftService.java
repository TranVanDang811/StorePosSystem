package com.possystem.backend.shift.service;

import com.possystem.backend.shift.dto.CloseShiftRequest;
import com.possystem.backend.shift.dto.OpenShiftRequest;
import com.possystem.backend.shift.dto.ShiftClosingReportResponse;
import com.possystem.backend.shift.dto.ShiftResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;

public interface ShiftService {

    ShiftResponse openShift(OpenShiftRequest request);

    ShiftResponse closeShift(CloseShiftRequest request);

    ShiftResponse getCurrentShift();
    ShiftClosingReportResponse getShiftReport();
    ResponseEntity<ByteArrayResource> exportShiftReport(String shiftCode);
}