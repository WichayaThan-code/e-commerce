package com.thaishopfun.catalog;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        assertEquals("SAMPLE-SHIRT", parsed.get(1).productKey());
        assertEquals("VARIABLE", parsed.get(0).productType());
        assertEquals("สี", parsed.get(0).option1Name());
        assertEquals("แดง", parsed.get(0).option1Value());
        assertEquals("ดำ", parsed.get(1).option1Value());
        assertEquals("ไซซ์", parsed.get(0).option2Name());
    }
}