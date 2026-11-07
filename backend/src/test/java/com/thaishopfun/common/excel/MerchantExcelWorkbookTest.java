package com.thaishopfun.common.excel;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantExcelWorkbookTest {

    @Test
    void buildsWorkbookWithMetaTableAndSafeText() throws Exception {
        byte[] bytes;
        try (var book = new MerchantExcelWorkbook()) {
            var meta = new LinkedHashMap<String, String>();
            meta.put("Timezone", "Asia/Bangkok");
            meta.put("Shop", "ร้านทดสอบ");
            book.addMetaSheet("Meta", meta);
            var columns = List.of(
                MerchantExcelWorkbook.Column.text("SKU"),
                MerchantExcelWorkbook.Column.money("Amount"),
                MerchantExcelWorkbook.Column.integer("Qty")
            );
            var rows = new ArrayList<List<Object>>();
            var r1 = new ArrayList<Object>();
            r1.add("00123");
            r1.add(new BigDecimal("-12.50"));
            r1.add(3);
            rows.add(r1);
            var r2 = new ArrayList<Object>();
            r2.add("=CMD|");
            r2.add(new BigDecimal("10.00"));
            r2.add(1);
            rows.add(r2);
            book.addTableSheet("Data", "DataTable", columns, rows);
            bytes = book.toBytes();
        }

        // OOXML zip signature
        assertEquals(0x50, bytes[0] & 0xff);
        assertEquals(0x4b, bytes[1] & 0xff);

        try (var in = new ByteArrayInputStream(bytes); var book = new XSSFWorkbook(in)) {
            assertEquals(2, book.getNumberOfSheets());
            var data = book.getSheet("Data");
            assertFalse(data.getTables().isEmpty());
            assertEquals(1, data.getPaneInformation() == null ? 0 : 1);
            assertEquals("SKU", data.getRow(0).getCell(0).getStringCellValue());
            assertEquals("00123", data.getRow(1).getCell(0).getStringCellValue());
            assertEquals(-12.50, data.getRow(1).getCell(1).getNumericCellValue(), 0.001);
            assertTrue(data.getRow(2).getCell(0).getStringCellValue().startsWith("'=")
                || data.getRow(2).getCell(0).getStringCellValue().startsWith("="));
            // formula-like text must remain a string cell (not a formula)
            assertFalse(data.getRow(2).getCell(0).getCellType().name().equals("FORMULA"));
        }
    }

    @Test
    void emptyDataStillHasTableStructure() throws Exception {
        byte[] bytes;
        try (var book = new MerchantExcelWorkbook()) {
            book.addTableSheet("Empty", "EmptyTable", List.of(
                MerchantExcelWorkbook.Column.text("A"),
                MerchantExcelWorkbook.Column.text("B")
            ), List.of());
            bytes = book.toBytes();
        }
        try (var in = new ByteArrayInputStream(bytes); var book = new XSSFWorkbook(in)) {
            var sheet = book.getSheet("Empty");
            assertFalse(sheet.getTables().isEmpty());
            assertEquals("A", sheet.getRow(0).getCell(0).getStringCellValue());
        }
    }
}
