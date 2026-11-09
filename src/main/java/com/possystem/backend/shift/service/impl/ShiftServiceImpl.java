package com.possystem.backend.shift.service.impl;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.possystem.backend.common.enums.ShiftStatus;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.util.mapper.ShiftMapper;
import com.possystem.backend.order.repository.OrderRepository;
import com.possystem.backend.payment.repository.PaymentRepository;
import com.possystem.backend.shift.dto.CloseShiftRequest;
import com.possystem.backend.shift.dto.OpenShiftRequest;
import com.possystem.backend.shift.dto.ShiftClosingReportResponse;
import com.possystem.backend.shift.dto.ShiftResponse;
import com.possystem.backend.shift.entity.Shift;
import com.possystem.backend.shift.repository.ShiftRepository;
import com.possystem.backend.shift.service.ShiftService;
import com.possystem.backend.user.entity.User;
import com.possystem.backend.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.apache.poi.ss.util.CellUtil.createCell;
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShiftServiceImpl implements ShiftService {
    final ShiftMapper shiftMapper;
    final ShiftRepository shiftRepository;
    final PaymentRepository paymentRepository;
    final UserService userService;
    @Override
    public ShiftResponse openShift(OpenShiftRequest request) {
        User currentUser = userService.getCurrentUser();
        shiftRepository.findByStatus(ShiftStatus.OPEN)
                .ifPresent(s -> {
                    throw new AppException(ErrorCode.SHIFT_ALREADY_OPENED);
                });

        Shift shift = Shift.builder()
                .shiftCode(generateShiftCode())
                .startTime(LocalDateTime.now())
                .note(request.getNote())
                .openingCash(request.getOpeningCash())
                .status(ShiftStatus.OPEN)
                .openedBy(currentUser)
                .build();

        return shiftMapper.toResponse(shiftRepository.save(shift));
    }

    @Override
    public ShiftResponse closeShift(CloseShiftRequest request) {

        // 1. Lấy user hiện tại
        User currentUser = userService.getCurrentUser();

        // 2. Lấy ca đang mở
        Shift shift = shiftRepository.findByStatus(ShiftStatus.OPEN)
                .orElseThrow(() -> new AppException(ErrorCode.NO_OPENED_SHIFT));

        LocalDateTime now = LocalDateTime.now();

        // =========================
        // 3. TIỀN BÁN (CASH)
        // =========================
        BigDecimal cashSales =
                paymentRepository.sumCashByShift(shift.getStartTime(), now);

        if (cashSales == null) {
            cashSales = BigDecimal.ZERO;
        }

        // =========================
        // 4. TIỀN REFUND (CASH)
        // =========================
        BigDecimal cashRefunds =
                paymentRepository.sumCashRefunds(shift.getStartTime(), now);

        if (cashRefunds == null) {
            cashRefunds = BigDecimal.ZERO;
        }

        // =========================
        // 5. EXPECTED CASH (CHUẨN POS)
        // =========================
        BigDecimal expectedCash =
                shift.getOpeningCash()
                        .add(cashSales)
                        .subtract(cashRefunds);

        // =========================
        // 6. TIỀN THỰC TẾ KIỂM
        // =========================
        BigDecimal countedCash = calculateCash(request);

        // =========================
        // 7. CHÊNH LỆCH (ÂM / DƯƠNG)
        // =========================
        BigDecimal difference =
                countedCash.subtract(expectedCash);

        // =========================
        // 8. TIỀN NỘP KÉT
        // =========================
        BigDecimal depositedCash =
                countedCash.subtract(shift.getOpeningCash());

        // =========================
        // 9. THỐNG KÊ
        // =========================
        long totalOrders =
                paymentRepository.countPaidOrders(shift.getStartTime(), now);

        long totalCustomers =
                paymentRepository.countCustomers(shift.getStartTime(), now);

        // =========================
        // 10. SET USER
        // =========================
        shift.setClosedBy(currentUser);

        // =========================
        // 11. SET MỆNH GIÁ TIỀN
        // =========================
        shift.setNote500k(request.getNote500k());
        shift.setNote200k(request.getNote200k());
        shift.setNote100k(request.getNote100k());
        shift.setNote50k(request.getNote50k());
        shift.setNote20k(request.getNote20k());
        shift.setNote10k(request.getNote10k());
        shift.setNote5k(request.getNote5k());
        shift.setNote2k(request.getNote2k());
        shift.setNote1k(request.getNote1k());

        // =========================
        // 12. SET DATA QUAN TRỌNG
        // =========================
        shift.setOpeningCash(shift.getOpeningCash());
        shift.setCashSales(cashSales);
        shift.setCashRefunds(cashRefunds);

        shift.setExpectedCash(expectedCash);
        shift.setCountedCash(countedCash);
        shift.setDifference(difference);
        shift.setDepositedCash(depositedCash);

        shift.setTotalOrders(totalOrders);
        shift.setTotalCustomers(totalCustomers);

        // =========================
        // 13. CLOSE SHIFT
        // =========================
        shift.setEndTime(now);
        shift.setStatus(ShiftStatus.CLOSED);

        // =========================
        // 14. SAVE
        // =========================
        return shiftMapper.toResponse(shiftRepository.save(shift));
    }

    @Override
    public ShiftResponse getCurrentShift() {

        Shift shift = shiftRepository.findByStatus(ShiftStatus.OPEN)
                .orElse(null);

        return shiftMapper.toResponse(shift);
    }

    private BigDecimal calculateCash(CloseShiftRequest r) {

        long total =
                safe(r.getNote500k()) * 500000L +
                        safe(r.getNote200k()) * 200000L +
                        safe(r.getNote100k()) * 100000L +
                        safe(r.getNote50k()) * 50000L +
                        safe(r.getNote20k()) * 20000L +
                        safe(r.getNote10k()) * 10000L +
                        safe(r.getNote5k()) * 5000L +
                        safe(r.getNote2k()) * 2000L +
                        safe(r.getNote1k()) * 1000L;

        return BigDecimal.valueOf(total);
    }

    @Override
    public ShiftClosingReportResponse getShiftReport() {

        Shift shift = shiftRepository
                .findTopByStatusOrderByEndTimeDesc(ShiftStatus.CLOSED)
                .orElseThrow(() -> new AppException(ErrorCode.NO_CLOSED_SHIFT));

        LocalDateTime start = shift.getStartTime();
        LocalDateTime end = shift.getEndTime();

        long totalOrders =
                paymentRepository.countPaidOrders(start, end);

        long totalCustomers =
                paymentRepository.countCustomers(start, end);

        return ShiftClosingReportResponse.builder()
                .startTime(start)
                .endTime(end)
                .openingCash(shift.getOpeningCash())
                .cashSales(shift.getCashSales())
                .cashRefunds(shift.getCashRefunds())
                .shiftCode(shift.getShiftCode())
                .expectedCash(shift.getExpectedCash())
                .countedCash(shift.getCountedCash())
                .difference(shift.getDifference())
                .depositedCash(shift.getDepositedCash())
           
                .closedById(
                        shift.getClosedBy() != null ? shift.getClosedBy().getId() : null
                )
                .closedByName(
                        shift.getClosedBy() != null ? shift.getClosedBy().getFullName() : null
                )

                .openedByName(
                        shift.getOpenedBy() != null ? shift.getOpenedBy().getFullName() : null
                )

                .note500k(shift.getNote500k())
                .note200k(shift.getNote200k())
                .note100k(shift.getNote100k())
                .note50k(shift.getNote50k())
                .note20k(shift.getNote20k())
                .note10k(shift.getNote10k())
                .note5k(shift.getNote5k())
                .note2k(shift.getNote2k())
                .note1k(shift.getNote1k())

                .totalOrdersPaid(totalOrders)
                .totalCustomers(totalCustomers)
                .build();
    }

    private long safe(Long value) {
        return value == null ? 0 : value;
    }

    private String generateShiftCode() {
        LocalDateTime now = LocalDateTime.now();
        return String.format("SHIFT-%d%02d%02d-%02d%02d%02d",
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                now.getHour(),
                now.getMinute(),
                now.getSecond()
        );
    }
    private PdfPCell createCell(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(align);
        return cell;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }

    //-----------------
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    @Transactional(readOnly = true)
    public ResponseEntity<String> exportShiftReportHtml(String shiftCode) {

        Shift shift = shiftRepository.findByShiftCode(shiftCode)
                .orElseThrow(() -> new AppException(ErrorCode.SHIFT_NOT_FOUND));

        ShiftClosingReportResponse report = shiftMapper.toClosingReportResponse(shift);

        String html = generateShiftHtml(report);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }
    private String generateShiftHtml(ShiftClosingReportResponse r) {

        String denominations = """
<div class="section">
    <div class="center bold">DENOMINATIONS</div>
    
    %s
    %s
    %s
    %s
    %s
    %s
    %s
    %s
    %s
</div>
""".formatted(
                denomRow("500,000", r.getNote500k()),
                denomRow("200,000", r.getNote200k()),
                denomRow("100,000", r.getNote100k()),
                denomRow("50,000", r.getNote50k()),
                denomRow("20,000", r.getNote20k()),
                denomRow("10,000", r.getNote10k()),
                denomRow("5,000", r.getNote5k()),
                denomRow("2,000", r.getNote2k()),
                denomRow("1,000", r.getNote1k())
        );

        return """
<html>
<head>
<style>
body {
    font-family: monospace;
    width: 80mm;
    padding: 10px;
    font-size: 13px;
}

.center { text-align: center; }

.line {
    display: flex;
    justify-content: space-between;
}

.bold { font-weight: bold; }

.small { font-size: 12px; }

hr {
    border-top: 1px dashed black;
    margin: 6px 0;
}
</style>
</head>

<body>

<div class="center">
    <h3>SHIFT REPORT</h3>
    <div>%s</div>
</div>

<hr/>

<div class="line"><span>Start</span><span>%s</span></div>
<div class="line"><span>End</span><span>%s</span></div>
<div class="line"><span>Opened</span><span>%s</span></div>
<div class="line"><span>Closed</span><span>%s</span></div>


<hr/>

%s

<hr/>

<div class="line"><span>Opening</span><span>%s</span></div>
<div class="line"><span>Sales</span><span>%s</span></div>
<div class="line"><span>Refund</span><span>%s</span></div>

<hr/>

<div class="line bold"><span>Expected</span><span>%s</span></div>
<div class="line bold"><span>Counted</span><span>%s</span></div>
<div class="line bold"><span>Diff</span><span>%s</span></div>

<hr/>

<div class="line"><span>Orders</span><span>%d</span></div>
<div class="line"><span>Customers</span><span>%d</span></div>

<hr/>

<div class="center small">
    Close shift OK 
</div>

</body>
</html>
""".formatted(
                r.getShiftCode(),
                formatDateTime(r.getStartTime()),
                formatDateTime(r.getEndTime()),
                r.getOpenedByName(),
                r.getClosedByName(),
                denominations,
                formatShort(r.getOpeningCash()),
                formatShort(r.getCashSales()),
                formatShort(r.getCashRefunds()),
                formatShort(r.getExpectedCash()),
                formatShort(r.getCountedCash()),
                formatShort(r.getDifference()),

                r.getTotalOrdersPaid(),
                r.getTotalCustomers()
        );
    }


    //---
    private String formatShort(BigDecimal amount) {
        if (amount == null) return "0";
        return String.format("%,.0f", amount);
    }
    private String denomRow(String label, Long count) {

        long c = count == null ? 0 : count;
        long value = Long.parseLong(label.replace(",", ""));
        long total = c * value;

        return """
<div class="line small">
    <span>%s x %d</span>
    <span>%s</span>
</div>
""".formatted(
                label,
                c,
                formatShort(BigDecimal.valueOf(total))
        );
    }
}