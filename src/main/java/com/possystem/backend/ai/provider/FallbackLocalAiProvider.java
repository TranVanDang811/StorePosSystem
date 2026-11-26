package com.possystem.backend.ai.provider;

import com.possystem.backend.ai.dto.*;
import com.possystem.backend.ai.tools.PosBusinessAiTools;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FallbackLocalAiProvider implements AiModelProvider {

    PosBusinessAiTools tools;
    Locale vnLocale = new Locale("vi", "VN");

    @Override
    public String getProviderName() {
        return "local-ai-engine";
    }

    @Override
    public String getModelName() {
        return "coffee-business-v1";
    }

    @Override
    public AiChatResponse chat(String userId, String conversationId, String userMessage, List<String> conversationHistory) {
        long startTime = System.currentTimeMillis();
        String lowerMsg = userMessage.trim().toLowerCase(vnLocale);
        List<String> executedTools = new ArrayList<>();
        StringBuilder reply = new StringBuilder();

        // 1. Prompt Injection & Modification Guardrail Check
        if (isSecurityViolation(lowerMsg)) {
            reply.append("⚠️ **Từ chối yêu cầu bảo mật:**\n\n")
                 .append("Hệ thống trợ lý AI Đẳng Cấp Coffee POS chỉ hoạt động ở chế độ **chỉ đọc (Read-Only)** để phục vụ phân tích dữ liệu kinh doanh.\n")
                 .append("- Không được phép thực thi SQL hoặc truy vấn tùy ý vào cơ sở dữ liệu.\n")
                 .append("- Không được phép sửa đổi, cập nhật hoặc xóa đơn hàng, tồn kho, thanh toán.\n")
                 .append("- Không được phép tiết lộ thông tin bí mật hệ thống hoặc can thiệp vào phân quyền.");
            return buildResponse(conversationId, reply.toString(), executedTools, startTime, 35, 75);
        }

        // 2. Intent matching
        if (lowerMsg.contains("doanh thu hôm nay") || lowerMsg.contains("doanh số hôm nay") || lowerMsg.contains("bán được bao nhiêu tiền")) {
            executedTools.add("getSalesSummary");
            SalesSummaryDto summary = tools.getSalesSummary(LocalDate.now(), LocalDate.now());
            reply.append("### ☕ Báo Cáo Doanh Thu Hôm Nay (").append(LocalDate.now()).append(")\n\n")
                 .append("- **Tổng doanh thu:** `").append(formatVnd(summary.getTotalRevenue())).append("`\n")
                 .append("- **Tổng số đơn hàng đã thanh toán:** `").append(summary.getTotalOrders()).append(" đơn`\n")
                 .append("- **Giá trị trung bình/đơn (AOV):** `").append(formatVnd(summary.getAverageOrderValue())).append("`\n")
                 .append("- **Tổng tiền hoàn lại:** `").append(formatVnd(summary.getTotalRefund())).append("`\n")
                 .append("- **Khách hàng thành viên:** `").append(summary.getMemberCustomerCount()).append("` | **Khách vãng lai:** `").append(summary.getGuestCustomerCount()).append("`\n\n");

            if (!summary.getRevenueByPaymentMethod().isEmpty()) {
                reply.append("#### 💳 Phân bổ phương thức thanh toán:\n");
                summary.getRevenueByPaymentMethod().forEach((method, amount) ->
                        reply.append("- **").append(method).append(":** `").append(formatVnd(amount)).append("`\n")
                );
            }
        } else if (lowerMsg.contains("tổng số đơn") || lowerMsg.contains("bao nhiêu đơn") || lowerMsg.contains("đơn hàng hôm nay")) {
            executedTools.add("getSalesSummary");
            SalesSummaryDto summary = tools.getSalesSummary(LocalDate.now(), LocalDate.now());
            reply.append("### 📦 Tình Hình Đơn Hàng Hôm Nay\n\n")
                 .append("- **Tổng số đơn hoàn tất:** `").append(summary.getTotalOrders()).append(" đơn`\n")
                 .append("- **Doanh thu đạt được:** `").append(formatVnd(summary.getTotalRevenue())).append("`\n")
                 .append("- **Giá trị bình quân/đơn:** `").append(formatVnd(summary.getAverageOrderValue())).append("`\n")
                 .append("- **Tỷ lệ khách thành viên:** `")
                 .append(summary.getTotalOrders() > 0 ? (summary.getMemberCustomerCount() * 100 / summary.getTotalOrders()) : 0)
                 .append("%`\n\n")
                 .append("💡 *Đề xuất:* Đẩy mạnh tiếp thị tích điểm để gia tăng tỷ lệ quay lại của khách hàng thân thiết.");
        } else if (lowerMsg.contains("bán chạy") || lowerMsg.contains("top sản phẩm") || lowerMsg.contains("món bán chạy")) {
            executedTools.add("getTopSellingProducts");
            TopSellingProductsDto top = tools.getTopSellingProducts(5, LocalDate.now().minusDays(30), LocalDate.now());
            reply.append("### 🏆 Top Sản Phẩm Bán Chạy Nhất (30 Ngày Qua)\n\n");
            if (top.getProducts().isEmpty()) {
                reply.append("Chưa có ghi nhận dữ liệu bán hàng trong giai đoạn này.\n");
            } else {
                reply.append("| STT | Mã SP | Tên Sản Phẩm | Danh Mục | Số Lượng Đã Bán | Doanh Thu |\n")
                     .append("|:---:|:------|:-------------|:---------|:---------------:|:----------|\n");
                int idx = 1;
                for (TopSellingProductsDto.ProductItem p : top.getProducts()) {
                    reply.append("| ").append(idx++).append(" | `").append(p.getProductCode()).append("` | **")
                         .append(p.getProductName()).append("** | ").append(p.getCategory()).append(" | ")
                         .append(p.getQuantitySold()).append(" | `").append(formatVnd(p.getTotalRevenue())).append("` |\n");
                }
            }
        } else if (lowerMsg.contains("lợi nhuận") || lowerMsg.contains("lời nhất") || lowerMsg.contains("biên lợi nhuận")) {
            executedTools.add("getProfitabilityReport");
            ProfitabilityReportDto prof = tools.getProfitabilityReport(5);
            reply.append("### 📈 Top Sản Phẩm Có Lợi Nhuận Cao Nhất\n\n")
                 .append("| STT | Tên Sản Phẩm | Giá Bán | Giá Vốn | Lợi Nhuận/Đơn Vị | Tỷ Suất LN |\n")
                 .append("|:---:|:-------------|:--------|:--------|:----------------:|:-----------|\n");
            int idx = 1;
            for (ProfitabilityReportDto.ProfitItem p : prof.getTopProfitableProducts()) {
                reply.append("| ").append(idx++).append(" | **").append(p.getName()).append("** | ")
                     .append(formatVnd(p.getSellingPrice())).append(" | ")
                     .append(formatVnd(p.getCostPrice())).append(" | `")
                     .append(formatVnd(p.getUnitProfit())).append("` | **")
                     .append(String.format("%.1f%%", p.getProfitMarginPercentage())).append("** |\n");
            }
            reply.append("\n💡 *Chiến lược:* Ưu tiên gợi ý upsell các món có tỷ suất lợi nhuận trên 60% khi nhân viên tư vấn.");
        } else if (lowerMsg.contains("sắp hết hàng") || lowerMsg.contains("hết hàng") || lowerMsg.contains("tồn kho thấp") || lowerMsg.contains("tồn kho")) {
            executedTools.add("getInventoryStatus");
            InventoryStatusDto inv = tools.getInventoryStatus(10);
            reply.append("### ⚠️ Báo Cáo Cảnh Báo Tồn Kho Đẳng Cấp Coffee\n\n")
                 .append("- **Sản phẩm đã hết hàng (Out of stock):** `").append(inv.getTotalOutOfStockCount()).append(" sản phẩm`\n")
                 .append("- **Sản phẩm sắp hết hàng (<= ").append(inv.getThresholdUsed()).append(" đơn vị):** `").append(inv.getTotalLowStockCount()).append(" sản phẩm`\n\n");

            if (!inv.getOutOfStockProducts().isEmpty()) {
                reply.append("#### 🚫 Danh sách sản phẩm hết hàng:\n");
                for (InventoryStatusDto.StockItem item : inv.getOutOfStockProducts()) {
                    reply.append("- `").append(item.getProductCode()).append("` - **").append(item.getName()).append("**: Tồn kho 0 ").append(item.getUnit()).append("\n");
                }
                reply.append("\n");
            }

            if (!inv.getLowStockProducts().isEmpty()) {
                reply.append("#### ⚠️ Danh sách sản phẩm sắp hết hàng:\n");
                for (InventoryStatusDto.StockItem item : inv.getLowStockProducts()) {
                    reply.append("- `").append(item.getProductCode()).append("` - **").append(item.getName()).append("**: Còn `").append(item.getCurrentStock()).append(" ").append(item.getUnit()).append("`\n");
                }
            }
            reply.append("\n💡 *Khuyến nghị:* Quản lý cần tạo phiếu nhập hàng (Import Order) ngay đối với các nguyên liệu chính để tránh gián đoạn phục vụ.");
        } else if (lowerMsg.contains("doanh thu theo ca") || lowerMsg.contains("ca") || lowerMsg.contains("tiền ca")) {
            executedTools.add("getShiftRevenue");
            ShiftRevenueDto shift = tools.getShiftRevenue(null, null);
            reply.append("### 🕒 Báo Cáo Doanh Thu Theo Ca Làm Việc\n\n")
                 .append("- **Mã ca:** `").append(shift.getShiftCode()).append("` (Trạng thái: **").append(shift.getStatus()).append("**)\n")
                 .append("- **Thu ngân phụ trách:** `").append(shift.getCashierName()).append("` | **Máy POS:** `").append(shift.getTerminalId()).append("`\n")
                 .append("- **Tiền mặt ban đầu ca:** `").append(formatVnd(shift.getInitialCash())).append("`\n")
                 .append("- **Doanh thu tiền mặt:** `").append(formatVnd(shift.getCashSales())).append("`\n")
                 .append("- **Tổng doanh thu ca:** `").append(formatVnd(shift.getTotalRevenue())).append("`\n")
                 .append("- **Số đơn hoàn tất:** `").append(shift.getTotalOrders()).append(" đơn`\n\n")
                 .append("💡 *Lưu ý:* Khi đóng ca, thu ngân cần kiểm kê tiền mặt thực tế trùng khớp với tổng tiền ban đầu + doanh thu tiền mặt.");
        } else if (lowerMsg.contains("so sánh") || lowerMsg.contains("hai khoảng thời gian") || lowerMsg.contains("tuần này") || lowerMsg.contains("tháng trước")) {
            executedTools.add("compareRevenue");
            LocalDate today = LocalDate.now();
            RevenueComparisonDto comp = tools.compareRevenue(today.minusDays(14), today.minusDays(7), today.minusDays(6), today);
            reply.append("### 📊 So Sánh Doanh Thu Giữa Hai Khoảng Thời Gian\n\n")
                 .append("- **Kỳ 1 (Tuần trước):** `").append(comp.getPeriod1Start()).append(" -> ").append(comp.getPeriod1End()).append("`\n")
                 .append("  - Doanh thu: `").append(formatVnd(comp.getPeriod1Revenue())).append("` | Đơn hàng: `").append(comp.getPeriod1Orders()).append(" đơn`\n")
                 .append("- **Kỳ 2 (Tuần này):** `").append(comp.getPeriod2Start()).append(" -> ").append(comp.getPeriod2End()).append("`\n")
                 .append("  - Doanh thu: `").append(formatVnd(comp.getPeriod2Revenue())).append("` | Đơn hàng: `").append(comp.getPeriod2Orders()).append(" đơn`\n\n")
                 .append("#### 📌 Đánh giá chênh lệch:\n")
                 .append("- **Chênh lệch doanh thu:** `").append(comp.getRevenueDifference().compareTo(BigDecimal.ZERO) >= 0 ? "+" : "")
                 .append(formatVnd(comp.getRevenueDifference())).append("`\n")
                 .append("- **Tỷ lệ tăng trưởng:** **").append(String.format("%.2f%%", comp.getRevenueGrowthPercentage())).append("**\n")
                 .append("- **Nhận định xu hướng:** *").append(comp.getTrendAnalysis()).append("*\n");
        } else if (lowerMsg.contains("xu hướng") || lowerMsg.contains("tình hình kinh doanh") || lowerMsg.contains("tổng quan")) {
            executedTools.add("getSalesSummary");
            executedTools.add("getTopSellingProducts");
            executedTools.add("getInventoryStatus");
            SalesSummaryDto sum = tools.getSalesSummary(LocalDate.now(), LocalDate.now());
            TopSellingProductsDto top = tools.getTopSellingProducts(3, LocalDate.now().minusDays(7), LocalDate.now());
            InventoryStatusDto inv = tools.getInventoryStatus(5);

            reply.append("### 📈 Phân Tích Xu Hướng Kinh Doanh & Đề Xuất Quản Trị\n\n")
                 .append("1. **Doanh số hiện tại:** Doanh thu hôm nay đạt `").append(formatVnd(sum.getTotalRevenue()))
                 .append("` trên `").append(summaryOrDefault(sum.getTotalOrders())).append("` đơn hàng.\n")
                 .append("2. **Sản phẩm dẫn đầu:** Các món bán chạy tiêu biểu gồm: ")
                 .append(top.getProducts().isEmpty() ? "Đang cập nhật" : top.getProducts().get(0).getProductName())
                 .append(". Cần duy trì nguồn nguyên liệu ổn định.\n")
                 .append("3. **Sức khỏe tồn kho:** Hiện có `").append(inv.getTotalOutOfStockCount()).append("` món hết hàng và `")
                 .append(inv.getTotalLowStockCount()).append("` món sắp hết hàng.\n\n")
                 .append("🎯 **Đề xuất hành động:**\n")
                 .append("- Duy trì nhập bổ sung kịp thời trước khung giờ cao điểm.\n")
                 .append("- Kích hoạt voucher tích điểm cho các khách hàng vãng lai nhằm chuyển đổi sang hội viên thân thiết.");
        } else {
            // General business response with suggestions
            reply.append("Xin chào Quản lý! Tôi là **Trợ lý Kinh doanh AI** của Đẳng Cấp Coffee POS.\n\n")
                 .append("Tôi có thể hỗ trợ bạn phân tích nhanh các thông số kinh doanh bằng ngôn ngữ tự nhiên:\n\n")
                 .append("- 💰 **Doanh thu hôm nay?** hoặc **Tổng số đơn hàng hôm nay?**\n")
                 .append("- 🏆 **Sản phẩm bán chạy nhất?** (Top món doanh thu cao)\n")
                 .append("- 📈 **Sản phẩm có lợi nhuận cao nhất?** (Biên lợi nhuận gộp)\n")
                 .append("- ⚠️ **Sản phẩm sắp hết hàng?** (Cảnh báo tồn kho)\n")
                 .append("- 🕒 **Doanh thu theo ca?** (Doanh thu ca làm việc hiện tại)\n")
                 .append("- 📊 **So sánh doanh thu giữa hai khoảng thời gian?**\n")
                 .append("- 🔍 **Phân tích xu hướng kinh doanh?**\n\n")
                 .append("Bạn hãy chọn hoặc nhập một câu hỏi để tôi hỗ trợ nhé!");
        }

        int promptTokens = Math.max(10, userMessage.length() / 4 + 25);
        int completionTokens = Math.max(20, reply.length() / 4);
        return buildResponse(conversationId, reply.toString(), executedTools, startTime, promptTokens, completionTokens);
    }

    private boolean isSecurityViolation(String msg) {
        return msg.contains("drop table") || msg.contains("delete from") || msg.contains("update ")
                || msg.contains("insert into") || msg.contains("alter table") || msg.contains("truncate ")
                || msg.contains("union select") || msg.contains("select * from") || msg.contains("exec(")
                || msg.contains("xóa đơn") || msg.contains("chỉnh sửa tồn kho") || msg.contains("thay đổi giá")
                || msg.contains("sửa đơn hàng") || msg.contains("hủy thanh toán")
                || msg.contains("ignore previous") || msg.contains("override prompt") || msg.contains("bypass security");
    }

    private AiChatResponse buildResponse(String conversationId, String reply, List<String> tools, long startTime, int pTokens, int cTokens) {
        long duration = System.currentTimeMillis() - startTime;
        BigDecimal cost = BigDecimal.valueOf((pTokens * 0.00015 / 1000.0) + (cTokens * 0.00060 / 1000.0));
        return AiChatResponse.builder()
                .conversationId(conversationId)
                .messageId(UUID.randomUUID().toString())
                .reply(reply)
                .executedTools(tools)
                .promptTokens(pTokens)
                .completionTokens(cTokens)
                .totalTokens(pTokens + cTokens)
                .costEstimateUsd(cost)
                .durationMs(duration)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private String formatVnd(BigDecimal amount) {
        if (amount == null) return "0 đ";
        NumberFormat nf = NumberFormat.getInstance(vnLocale);
        return nf.format(amount) + " đ";
    }

    private String summaryOrDefault(long val) {
        return val + " đơn";
    }
}
