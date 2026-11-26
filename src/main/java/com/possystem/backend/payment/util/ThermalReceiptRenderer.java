package com.possystem.backend.payment.util;

import com.possystem.backend.common.util.CurrencyUtils;
import com.possystem.backend.order.dto.OrderDetailResponse;
import com.possystem.backend.order.dto.OrderResponse;
import com.possystem.backend.payment.dto.PaymentResponse;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * Tiện ích kết xuất mẫu in nhiệt (Thermal Print HTML Receipt) cho hóa đơn bán hàng.
 * Tách biệt hoàn toàn tầng trình diễn giao diện (Presentation) khỏi dịch vụ thanh toán cốt lõi (SRP).
 */
public final class ThermalReceiptRenderer {

    private ThermalReceiptRenderer() {
    }

    public static String render(PaymentResponse payment, OrderResponse order, String username) {
        StringBuilder items = new StringBuilder();

        for (OrderDetailResponse item : order.getOrderDetails()) {
            BigDecimal subtotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

            items.append("""
                    <div class="item">
                        <div class="name">%s</div>
                        <div class="line small">
                            <span>%dx%s</span>
                            <span>%s</span>
                        </div>
                    </div>
                    """.formatted(
                    item.getName(),
                    item.getQuantity(),
                    CurrencyUtils.formatShort(item.getPrice()),
                    CurrencyUtils.formatShort(subtotal)));
        }

        BigDecimal orderDiscount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal pointDiscount = payment.getPointDiscount() != null ? payment.getPointDiscount() : BigDecimal.ZERO;
        BigDecimal totalDiscount = orderDiscount.add(pointDiscount);

        return """
                <html>
                <head>
                    <style>
                        body {
                            font-family: monospace;
                            width: 80mm;
                            margin: 0;
                            padding: 10px;
                            font-size: 13px;
                        }
                        .center { text-align: center; }
                        .line { display: flex; justify-content: space-between; }
                        .small { font-size: 12px; }
                        .name { font-weight: 500; }
                        hr { border-top: 1px dashed black; margin: 6px 0; }
                        .total { font-weight: bold; font-size: 15px; }
                        .section { margin-top: 5px; }
                        .footer { text-align: right; font-size: 12px; margin-top: 10px; }
                    </style>
                </head>
                <body>
                    <div class="center">
                        <h3>DANG CAP STORE</h3>
                        <div>Hotline: 0123 456 789</div>
                        <div>--------------------------</div>
                    </div>

                    <div class="section small">
                        <div>Date: %s</div>
                        <div>Customer: %s</div>
                    </div>

                    <hr/>
                    %s
                    <hr/>

                    <div class="line"><span>Total</span><span>%s</span></div>
                    <div class="line"><span>Discount</span><span>-%s</span></div>
                    <div class="line total"><span>Final</span><span>%s</span></div>

                    <hr/>

                    <div class="line"><span>Method</span><span>%s</span></div>
                    <div class="line"><span>Received</span><span>%s</span></div>
                    <div class="line"><span>Change</span><span>%s</span></div>

                    <hr/>

                    <div class="footer">
                        Txn: %s <br/>
                        Staff: %s
                    </div>

                    <hr/>

                    <div class="center small">
                        Thank you <br/>
                        See you again!
                    </div>
                </body>
                </html>
                """.formatted(
                payment.getPaymentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                payment.getFullName() != null ? payment.getFullName() : "Guest",
                items.toString(),
                CurrencyUtils.formatShort(order.getTotalPrice()),
                CurrencyUtils.formatShort(totalDiscount),
                CurrencyUtils.formatShort(payment.getAmount()),
                payment.getMethod() != null ? payment.getMethod().name() : "CASH",
                CurrencyUtils.formatShort(payment.getCashReceived()),
                CurrencyUtils.formatShort(payment.getChangeAmount()),
                payment.getTransactionId(),
                username);
    }
}
