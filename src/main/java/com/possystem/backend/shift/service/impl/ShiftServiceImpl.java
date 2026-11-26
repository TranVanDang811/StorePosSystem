package com.possystem.backend.shift.service.impl;

import com.possystem.backend.common.enums.ShiftStatus;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.observability.PosBusinessMetrics;
import com.possystem.backend.common.outbox.event.PosEventType;
import com.possystem.backend.common.outbox.service.OutboxPublisher;
import com.possystem.backend.common.util.mapper.ShiftMapper;
import com.possystem.backend.shift.util.ShiftReportRenderer;
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
    final OutboxPublisher outboxPublisher;
    final PosBusinessMetrics businessMetrics;
    @Override
    @Transactional
    public ShiftResponse openShift(OpenShiftRequest request) {
        User currentUser = userService.getCurrentUser();
        String terminalId = (request.getTerminalId() != null && !request.getTerminalId().isBlank())
                ? request.getTerminalId().trim()
                : "MAIN_COUNTER";

        shiftRepository.findByStatusAndTerminalId(ShiftStatus.OPEN, terminalId)
                .ifPresent(s -> {
                    throw new AppException(ErrorCode.SHIFT_ALREADY_OPENED, "Ca làm việc tại quầy " + terminalId + " đã được mở (" + s.getShiftCode() + ")");
                });

        shiftRepository.findByStatus(ShiftStatus.OPEN)
                .ifPresent(s -> {
                    if (s.getTerminalId() == null || s.getTerminalId().equalsIgnoreCase(terminalId)) {
                        throw new AppException(ErrorCode.SHIFT_ALREADY_OPENED);
                    }
                });

        Shift shift = Shift.builder()
                .shiftCode(generateShiftCode())
                .startTime(LocalDateTime.now())
                .terminalId(terminalId)
                .note(request.getNote())
                .openingCash(request.getOpeningCash())
                .status(ShiftStatus.OPEN)
                .openedBy(currentUser)
                .build();

        Shift saved = shiftRepository.save(shift);
        if (businessMetrics != null) {
            businessMetrics.recordShiftOpened(saved.getTerminalId());
        }
        return shiftMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ShiftResponse closeShift(CloseShiftRequest request) {

        // 1. Lấy user hiện tại
        User currentUser = userService.getCurrentUser();

        // 2. Lấy ca đang mở
        String terminalId = (request.getTerminalId() != null && !request.getTerminalId().isBlank())
                ? request.getTerminalId().trim()
                : "MAIN_COUNTER";

        Shift shift = shiftRepository.findByStatusAndTerminalId(ShiftStatus.OPEN, terminalId)
                .or(() -> shiftRepository.findByStatus(ShiftStatus.OPEN))
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
        // 14. SAVE & EMIT SHIFT_CLOSED OUTBOX EVENT
        // =========================
        Shift savedShift = shiftRepository.save(shift);
        if (outboxPublisher != null) {
            java.util.Map<String, Object> shiftPayload = new java.util.LinkedHashMap<>();
            shiftPayload.put("shiftId", savedShift.getId());
            shiftPayload.put("shiftCode", savedShift.getShiftCode());
            shiftPayload.put("terminalId", savedShift.getTerminalId());
            shiftPayload.put("openedByName", savedShift.getOpenedBy() != null ? savedShift.getOpenedBy().getFullName() : null);
            shiftPayload.put("closedByName", currentUser != null ? currentUser.getFullName() : null);
            shiftPayload.put("openingCash", savedShift.getOpeningCash());
            shiftPayload.put("cashSales", savedShift.getCashSales());
            shiftPayload.put("cashRefunds", savedShift.getCashRefunds());
            shiftPayload.put("expectedCash", savedShift.getExpectedCash());
            shiftPayload.put("countedCash", savedShift.getCountedCash());
            shiftPayload.put("difference", savedShift.getDifference());
            shiftPayload.put("totalOrders", savedShift.getTotalOrders());
            shiftPayload.put("totalCustomers", savedShift.getTotalCustomers());
            outboxPublisher.publish(
                    "SHIFT",
                    savedShift.getId() != null ? savedShift.getId() : savedShift.getShiftCode(),
                    PosEventType.SHIFT_CLOSED,
                    "shift:" + (savedShift.getId() != null ? savedShift.getId() : savedShift.getShiftCode()),
                    savedShift.getShiftCode(),
                    shiftPayload);
        }
        if (businessMetrics != null) {
            businessMetrics.recordShiftClosed(savedShift.getTerminalId(), savedShift.getDifference());
        }
        return shiftMapper.toResponse(savedShift);
    }

    @Override
    public ShiftResponse getCurrentShift() {
        return getCurrentShift(null);
    }

    @Override
    public ShiftResponse getCurrentShift(String terminalId) {
        Shift shift = null;
        if (terminalId != null && !terminalId.isBlank()) {
            shift = shiftRepository.findByStatusAndTerminalId(ShiftStatus.OPEN, terminalId.trim()).orElse(null);
        }
        if (shift == null) {
            shift = shiftRepository.findByStatus(ShiftStatus.OPEN).orElse(null);
        }
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
    //-----------------
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    @Transactional(readOnly = true)
    public ResponseEntity<String> exportShiftReportHtml(String shiftCode) {

        Shift shift = shiftRepository.findByShiftCode(shiftCode)
                .orElseThrow(() -> new AppException(ErrorCode.SHIFT_NOT_FOUND));

        ShiftClosingReportResponse report = shiftMapper.toClosingReportResponse(shift);
        String html = ShiftReportRenderer.render(report);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }
}