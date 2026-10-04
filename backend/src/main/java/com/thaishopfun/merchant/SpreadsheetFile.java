package com.thaishopfun.merchant;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class SpreadsheetFile {
    private SpreadsheetFile() {}

    static byte[] xlsx(List<String> headers, List<List<String>> rows) {
        var sheet = new StringBuilder();
        sheet.append("""
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
            """);
        sheet.append(row(1, headers));
        for (int index = 0; index < rows.size(); index++) sheet.append(row(index + 2, rows.get(index)));
        sheet.append("</sheetData></worksheet>");
        try {
            var output = new ByteArrayOutputStream();
            try (var zip = new ZipOutputStream(output)) {
                entry(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    </Types>
                    """);
                entry(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>
                    """);
                entry(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <sheets><sheet name="ใบสมัครร้านค้า" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>
                    """);
                entry(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    </Relationships>
                    """);
                entry(zip, "xl/worksheets/sheet1.xml", sheet.toString());
            }
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("ไม่สามารถสร้างไฟล์ Excel ได้", exception);
        }
    }

    private static String row(int number, List<String> values) {
        var xml = new StringBuilder("<row r=\"").append(number).append("\">");
        for (int index = 0; index < values.size(); index++) {
            xml.append("<c r=\"").append(column(index)).append(number).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                .append(escape(values.get(index))).append("</t></is></c>");
        }
        return xml.append("</row>").toString();
    }

    private static String column(int index) {
        var name = new StringBuilder();
        var value = index;
        do {
            name.insert(0, (char) ('A' + (value % 26)));
            value = value / 26 - 1;
        } while (value >= 0);
        return name.toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void entry(ZipOutputStream zip, String name, String xml) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
