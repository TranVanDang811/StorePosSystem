package com.possystem.backend.importorder.util;

import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.importorder.entity.ImportOrder;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Tiện ích kết xuất danh sách phiếu nhập kho sang định dạng Excel (Apache POI).
 * Tách biệt hoàn toàn các thư viện I/O nhị phân cấp thấp khỏi Service nhập hàng (DIP / SRP).
 */
public final class ImportOrderExcelExporter {

    private ImportOrderExcelExporter() {
    }

    public static ByteArrayInputStream exportToExcel(List<ImportOrder> orders) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Import Orders");

            Row header = sheet.createRow(0);
            String[] columns = {"Mã phiếu", "Nhà cung cấp", "Ngày tạo", "Trạng thái", "Tổng số lượng", "Tổng tiền (VNĐ)"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            int rowIdx = 1;
            for (ImportOrder order : orders) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(order.getId() != null ? order.getId() : "");
                row.createCell(1).setCellValue(order.getSupplier() != null ? order.getSupplier().getName() : "");
                row.createCell(2).setCellValue(order.getImportDate() != null ? order.getImportDate().format(formatter) : "");
                row.createCell(3).setCellValue(order.getStatus() != null ? order.getStatus().name() : "");
                row.createCell(4).setCellValue(order.getTotalQuantity());
                row.createCell(5).setCellValue(order.getTotalPrice() != null ? order.getTotalPrice().doubleValue() : 0.0);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
