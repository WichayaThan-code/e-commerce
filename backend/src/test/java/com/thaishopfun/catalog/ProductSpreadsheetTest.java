package com.thaishopfun.catalog;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductSpreadsheetTest {
    private static final byte[] PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Test
    void roundTripsSeveralRows() {
        var rows = List.of(
            new ProductSpreadsheet.SheetRow(2, "ชาไทย", "Thai tea", null, "หอม", null, null, "อาหาร", null, "TEA-1", null, null, new BigDecimal("45.00"), null, 100, 5, "https://cdn.example/tea.png", List.of(), null, null, null, null, null, null, null, null, null),
            new ProductSpreadsheet.SheetRow(3, "ขนม", null, null, null, null, null, null, "แบรนด์", "SNK-1", "885", "ถุง", new BigDecimal("20"), new BigDecimal("30"), null, 0, null, List.of(), null, null, null, null, null, null, null, null, null)
        );
        var parsed = ProductSpreadsheet.read(new ByteArrayInputStream(ProductSpreadsheet.write(rows)));
        assertEquals(2, parsed.size());
        assertEquals("ชาไทย", parsed.get(0).nameTh());
        assertEquals("TEA-1", parsed.get(0).skuCode());
        assertEquals(0, parsed.get(0).price().compareTo(new BigDecimal("45.00")));
        assertEquals(5, parsed.get(0).stock());
        assertEquals("https://cdn.example/tea.png", parsed.get(0).image());
        assertEquals("ขนม", parsed.get(1).nameTh());
        assertEquals("SNK-1", parsed.get(1).skuCode());
        assertNull(parsed.get(1).image());
        assertNull(parsed.get(0).productKey());
        assertNull(parsed.get(0).option1Name());
    }

    @Test
    void readsPictureAnchoredToTheProductRow() throws Exception {
        byte[] file;
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("products");
            var header = sheet.createRow(0);
            for (var index = 0; index < ProductSpreadsheet.HEADERS.length; index++) {
                header.createCell(index).setCellValue(ProductSpreadsheet.HEADERS[index]);
            }
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("ชาไทย");
            row.createCell(8).setCellValue("TEA-1");
            row.createCell(11).setCellValue(45);
            var picture = book.addPicture(PNG, Workbook.PICTURE_TYPE_PNG);
            var drawing = sheet.createDrawingPatriarch();
            drawing.createPicture(new XSSFClientAnchor(0, 0, 0, 0, 15, 1, 16, 2), picture);
            book.write(out);
            file = out.toByteArray();
        }
        var parsed = ProductSpreadsheet.read(new ByteArrayInputStream(file));
        assertEquals(1, parsed.size());
        assertEquals("ชาไทย", parsed.get(0).nameTh());
        assertNull(parsed.get(0).imageError());
        assertEquals(1, parsed.get(0).pictures().size());
        assertEquals("image/png", parsed.get(0).pictures().get(0).contentType());
        assertArrayEquals(PNG, parsed.get(0).pictures().get(0).data());
    }

    @Test
    void readsALegacySixteenColumnFile() throws Exception {
        byte[] file;
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("products");
            var header = sheet.createRow(0);
            for (var index = 0; index < 16; index++) header.createCell(index).setCellValue(ProductSpreadsheet.HEADERS[index]);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("ชาไทย");
            row.createCell(8).setCellValue("TEA-1");
            row.createCell(11).setCellValue(45);
            book.write(out);
            file = out.toByteArray();
        }
        var parsed = ProductSpreadsheet.read(new ByteArrayInputStream(file));
        assertEquals(1, parsed.size());
        assertEquals("ชาไทย", parsed.get(0).nameTh());
        assertEquals("TEA-1", parsed.get(0).skuCode());
        assertNull(parsed.get(0).productKey());
        assertNull(parsed.get(0).productType());
        assertNull(parsed.get(0).option1Value());
    }

    @Test
    void templateCarriesOneVariableProduct() {
        var parsed = ProductSpreadsheet.read(new ByteArrayInputStream(ProductSpreadsheet.template()));
        assertEquals(2, parsed.size());
        assertEquals("SAMPLE-SHIRT", parsed.get(0).productKey());
        assertEquals("VARIABLE", parsed.get(0).productType());
        assertEquals("สี", parsed.get(0).option1Name());
        assertEquals("แดง", parsed.get(0).option1Value());
        assertNull(parsed.get(1).productKey());
        assertEquals("SIMPLE", parsed.get(1).productType());
        assertEquals("TEA-1", parsed.get(1).skuCode());
    }

    @Test
    void writeExportHasSingleSheetTableAndTypedCells() throws Exception {
        var rows = List.of(
            new ProductSpreadsheet.ListExportRow(
                "ชาไทย", "Thai Tea", "00123", "เครื่องดื่ม",
                new BigDecimal("45.50"), new BigDecimal("45.50"), 5, "PUBLISHED",
                java.time.Instant.parse("2026-11-11T06:22:00Z"))
        );
        byte[] bytes = ProductSpreadsheet.writeExport(rows, Locale.forLanguageTag("th"));
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(1, book.getNumberOfSheets());
            assertEquals("สินค้า", book.getSheetName(0));
            var sheet = book.getSheetAt(0);
            assertEquals(1, sheet.getTables().size());
            assertNotNull(sheet.getPaneInformation());
            assertTrue(sheet.getPaneInformation().isFreezePane());
            assertEquals("ชื่อสินค้า (ไทย)", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("SKU", sheet.getRow(0).getCell(2).getStringCellValue());
            assertEquals("ชาไทย", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("00123", sheet.getRow(1).getCell(2).getStringCellValue());
            assertEquals(CellType.STRING, sheet.getRow(1).getCell(2).getCellType());
            assertEquals(CellType.NUMERIC, sheet.getRow(1).getCell(4).getCellType());
            assertEquals(45.50, sheet.getRow(1).getCell(4).getNumericCellValue(), 0.001);
            assertFalse(DateUtil.isCellDateFormatted(sheet.getRow(1).getCell(4)));
            assertEquals(CellType.NUMERIC, sheet.getRow(1).getCell(6).getCellType());
            assertEquals(5, (int) sheet.getRow(1).getCell(6).getNumericCellValue());
            assertEquals("PUBLISHED", sheet.getRow(1).getCell(7).getStringCellValue());
        }
    }

    @Test
    void sheetNameFollowsLocale() {
        assertEquals("สินค้า", ProductSpreadsheet.sheetName(Locale.forLanguageTag("th")));
        assertEquals("Products", ProductSpreadsheet.sheetName(Locale.ENGLISH));
        assertEquals("商品", ProductSpreadsheet.sheetName(Locale.SIMPLIFIED_CHINESE));
    }
}