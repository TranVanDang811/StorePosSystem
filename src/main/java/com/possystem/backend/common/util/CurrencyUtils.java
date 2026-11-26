package com.possystem.backend.common.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Tiện ích dùng chung để xử lý số học, chuyển đổi kiểu dữ liệu tiền tệ và định dạng tiền tệ VNĐ.
 * Giúp loại bỏ hoàn toàn mã trùng lặp (DRY) giữa OrderService, PaymentService và ShiftService.
 */
public final class CurrencyUtils {

    private CurrencyUtils() {
        // Utility class không cho phép khởi tạo
    }

    /**
     * Chuyển đổi an toàn một Object (BigDecimal, Long, Integer, Double, String) sang BigDecimal.
     * Trả về BigDecimal.ZERO nếu null hoặc không thể phân tích cú pháp.
     */
    public static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Định dạng tiền tệ rút gọn theo chuẩn phân tách hàng nghìn (vd: 50,000 hoặc 0).
     */
    public static String formatShort(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return String.format(Locale.US, "%,.0f", amount);
    }

    /**
     * Định dạng tiền tệ có ký hiệu đơn vị tiền tệ VNĐ (vd: 50.000 ₫).
     */
    public static String formatVnd(BigDecimal amount) {
        if (amount == null) {
            return "0 ₫";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("vi", "VN"));
        symbols.setGroupingSeparator('.');
        DecimalFormat decimalFormat = new DecimalFormat("#,##0 ₫", symbols);
        return decimalFormat.format(amount);
    }
}
