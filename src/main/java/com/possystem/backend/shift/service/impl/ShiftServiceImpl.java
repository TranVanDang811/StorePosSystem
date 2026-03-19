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
        shift.setOpeningCash(shift.getOpeningCash()); // giữ nguyên
        shift.setCashSales(cashSales);               // 👈 thêm mới
        shift.setCashRefunds(cashRefunds);           // 👈 thêm mới

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
                // 👇 NHÂN VIÊN KẾT CA
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

    private void addRow(PdfPTable table, String label, Integer value, Font font) {
        table.addCell(createCell(label, font, Element.ALIGN_LEFT));
        table.addCell(createCell(String.valueOf(value), font, Element.ALIGN_RIGHT));
    }

    private PdfPCell createCell(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(align);
        return cell;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0 đ";

        NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        return formatter.format(amount);
    }
    //-----------------
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    @Transactional(readOnly = true)
    public ResponseEntity<ByteArrayResource> exportShiftReport(String shiftCode) {

        Shift shift = shiftRepository.findByShiftCode(shiftCode)
                .orElseThrow(() -> new AppException(ErrorCode.SHIFT_NOT_FOUND));

        ShiftClosingReportResponse report = shiftMapper.toClosingReportResponse(shift);

        byte[] pdfBytes = generateShiftReportPdf(report);

        ByteArrayResource resource = new ByteArrayResource(pdfBytes);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=shift-" + shiftCode + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }
    private byte[] generateShiftReportPdf(ShiftClosingReportResponse report) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Document document = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(document, out);
            document.open();

            // ===== FONT =====
            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            Font headerFont = new Font(Font.HELVETICA, 13, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 11, Font.NORMAL);
            Font boldFont = new Font(Font.HELVETICA, 11, Font.BOLD);

            // ===== HEADER =====
            Paragraph store = new Paragraph("POS SYSTEM STORE", headerFont);
            store.setAlignment(Element.ALIGN_CENTER);

            Paragraph title = new Paragraph("SHIFT CLOSING REPORT", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);

            document.add(store);
            document.add(title);

            addSeparatorLine(document);

            // ===== INFO =====
            PdfPTable info = new PdfPTable(2);
            info.setWidthPercentage(100);
            info.setWidths(new float[]{40, 60});

            addRowNoBorder(info, "Shift:", report.getShiftCode(), normalFont);
            addRowNoBorder(info, "Opened:", report.getOpenedByName(), normalFont);
            addRowNoBorder(info, "Closed:", report.getClosedByName(), normalFont);
            addRowNoBorder(info, "Start:", formatDateTime(report.getStartTime()), normalFont);
            addRowNoBorder(info, "End:", formatDateTime(report.getEndTime()), normalFont);

            document.add(info);

            addSeparatorLine(document);

            // ===== CASH SUMMARY =====
            Paragraph cashHeader = new Paragraph("CASH SUMMARY", headerFont);
            cashHeader.setSpacingBefore(10);
            cashHeader.setSpacingAfter(5);
            document.add(cashHeader);

            PdfPTable cash = new PdfPTable(2);
            cash.setWidthPercentage(100);
            cash.setWidths(new float[]{70, 30});

            addRowNoBorder(cash, "Opening Cash", formatCurrency(report.getOpeningCash()), normalFont);
            addRowNoBorder(cash, "Cash Sales", formatCurrency(report.getCashSales()), normalFont);
            addRowNoBorder(cash, "Refunds", formatCurrency(report.getCashRefunds()), normalFont);

            document.add(cash);
            addSeparatorLine(document);

            PdfPTable cashTotal = new PdfPTable(2);
            cashTotal.setWidthPercentage(100);
            cashTotal.setWidths(new float[]{70, 30});

            addRowNoBorder(cashTotal, "Expected Cash", formatCurrency(report.getExpectedCash()), boldFont);
            addRowNoBorder(cashTotal, "Counted Cash", formatCurrency(report.getCountedCash()), boldFont);
            addRowNoBorder(cashTotal, "Difference", formatCurrency(report.getDifference()), boldFont);
            addRowNoBorder(cashTotal, "Deposited", formatCurrency(report.getDepositedCash()), boldFont);

            document.add(cashTotal);

            addSeparatorLine(document);

            // ===== DENOMINATIONS =====
            Paragraph denomHeader = new Paragraph("DENOMINATIONS", headerFont);
            denomHeader.setSpacingBefore(10);
            denomHeader.setSpacingAfter(5);
            document.add(denomHeader);

            PdfPTable denom = new PdfPTable(3);
            denom.setWidthPercentage(100);
            denom.setWidths(new float[]{30, 30, 40});

            addDenomRow(denom, "500,000", report.getNote500k(), normalFont);
            addDenomRow(denom, "200,000", report.getNote200k(), normalFont);
            addDenomRow(denom, "100,000", report.getNote100k(), normalFont);
            addDenomRow(denom, "50,000", report.getNote50k(), normalFont);
            addDenomRow(denom, "20,000", report.getNote20k(), normalFont);
            addDenomRow(denom, "10,000", report.getNote10k(), normalFont);
            addDenomRow(denom, "5,000", report.getNote5k(), normalFont);
            addDenomRow(denom, "2,000", report.getNote2k(), normalFont);
            addDenomRow(denom, "1,000", report.getNote1k(), normalFont);

            document.add(denom);

            addSeparatorLine(document);

            // ===== STATISTICS =====
            Paragraph statHeader = new Paragraph("STATISTICS", headerFont);
            statHeader.setSpacingBefore(10);
            statHeader.setSpacingAfter(5);
            document.add(statHeader);

            PdfPTable stat = new PdfPTable(2);
            stat.setWidthPercentage(100);
            stat.setWidths(new float[]{70, 30});

            addRowNoBorder(stat, "Orders", String.valueOf(report.getTotalOrdersPaid()), normalFont);
            addRowNoBorder(stat, "Customers", String.valueOf(report.getTotalCustomers()), normalFont);

            document.add(stat);

            addSeparatorLine(document);

            // ===== SIGN =====
            Paragraph sign = new Paragraph("Signature: " + report.getClosedByName(), normalFont);
            sign.setAlignment(Element.ALIGN_RIGHT);
            sign.setSpacingBefore(20);
            document.add(sign);

            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            log.error("PDF error: {}", e.getMessage());
            throw new RuntimeException("Export PDF failed", e);
        }
    }


    //---
    private void addRowNoBorder(PdfPTable table, String left, String right, Font font) {
        PdfPCell leftCell = new PdfPCell(new Phrase(left, font));
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        leftCell.setPadding(4);

        PdfPCell rightCell = new PdfPCell(new Phrase(right, font));
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rightCell.setPadding(4);

        table.addCell(leftCell);
        table.addCell(rightCell);
    }
    private void addDenomRow(PdfPTable table, String label, Long count, Font font) {
        long c = count == null ? 0 : count;
        long total = c * Long.parseLong(label.replace(",", ""));

        table.addCell(noBorderCell(label, font, Element.ALIGN_LEFT));
        table.addCell(noBorderCell("x " + c, font, Element.ALIGN_CENTER));
        table.addCell(noBorderCell(formatCurrency(BigDecimal.valueOf(total)), font, Element.ALIGN_RIGHT));
    }
    private PdfPCell noBorderCell(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(align);
        cell.setPadding(4);
        return cell;
    }
    private void addSeparatorLine(Document document) throws DocumentException {
        LineSeparator ls = new LineSeparator();
        ls.setLineWidth(1f);
        document.add(new Chunk(ls));
    }
}