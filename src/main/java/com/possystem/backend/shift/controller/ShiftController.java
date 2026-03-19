package com.possystem.backend.shift.controller;

import com.possystem.backend.shift.dto.CloseShiftRequest;
import com.possystem.backend.shift.dto.OpenShiftRequest;
import com.possystem.backend.shift.dto.ShiftClosingReportResponse;
import com.possystem.backend.shift.dto.ShiftResponse;
import com.possystem.backend.shift.service.ShiftService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.possystem.backend.common.response.ApiResponse;
@RestController
@RequestMapping("/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService shiftService;

    @PostMapping("/open")
    public ApiResponse<ShiftResponse> openShift(@RequestBody OpenShiftRequest request) {
        return ApiResponse.<ShiftResponse>builder()
                .result(shiftService.openShift(request))
                .build();
    }

    @PostMapping("/close")
    public ApiResponse<ShiftResponse> closeShift(@RequestBody CloseShiftRequest request) {
        return ApiResponse.<ShiftResponse>builder()
                .result(shiftService.closeShift(request))
                .build();
    }

    @GetMapping("/current")
    public ApiResponse<ShiftResponse> getCurrentShift() {
        return ApiResponse.<ShiftResponse>builder()
                .result(shiftService.getCurrentShift())
                .build();
    }

    @GetMapping("/report")
    public ShiftClosingReportResponse getShiftReport() {
        return shiftService.getShiftReport();
    }

    @GetMapping("/export/{shiftCode}")
    public ResponseEntity<ByteArrayResource> exportShiftReport(
            @PathVariable String shiftCode
    ) {
        return shiftService.exportShiftReport(shiftCode);
    }
}