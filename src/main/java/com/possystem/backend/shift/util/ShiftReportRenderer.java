package com.possystem.backend.shift.util;

import com.possystem.backend.common.util.CurrencyUtils;
import com.possystem.backend.shift.dto.ShiftClosingReportResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Tiện ích kết xuất mẫu in phiếu bàn giao ca (HTML Shift Report) cho nhân viên thu ngân.
 * Tách biệt hoàn toàn tầng trình diễn giao diện (Presentation) khỏi dịch vụ quản lý ca (SRP).
 */
public final class ShiftReportRenderer {

    private ShiftReportRenderer() {
    }

    public static String render(ShiftClosingReportResponse r) {
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
                r.getOpenedByName() != null ? r.getOpenedByName() : "",
                r.getClosedByName() != null ? r.getClosedByName() : "",
                denominations,
                CurrencyUtils.formatShort(r.getOpeningCash()),
                CurrencyUtils.formatShort(r.getCashSales()),
                CurrencyUtils.formatShort(r.getCashRefunds()),
                CurrencyUtils.formatShort(r.getExpectedCash()),
                CurrencyUtils.formatShort(r.getCountedCash()),
                CurrencyUtils.formatShort(r.getDifference()),
                r.getTotalOrdersPaid(),
                r.getTotalCustomers()
        );
    }

    private static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }

    private static String denomRow(String label, Long count) {
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
                CurrencyUtils.formatShort(BigDecimal.valueOf(total))
        );
    }
}
