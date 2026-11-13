package com.thaishopfun.common.excel;

import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTable;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableColumn;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableColumns;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableStyleInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared merchant Excel (.xlsx) builder: meta sheet, styled Excel Tables (AutoFilter), freeze panes,
 * numeric/date/text cells, and formula-injection-safe text. Uses Apache POI (already on the classpath).
 */
public final class MerchantExcelWorkbook implements AutoCloseable {
    public static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    public static final int MAX_COL_WIDTH = 60 * 256;
    public static final int MIN_COL_WIDTH = 10 * 256;

    public enum ColumnType { TEXT, INTEGER, DECIMAL, MONEY, PERCENT, DATETIME, DATE }

    public record Column(String header, ColumnType type) {
        public static Column text(String header) { return new Column(header, ColumnType.TEXT); }
        public static Column integer(String header) { return new Column(header, ColumnType.INTEGER); }
        public static Column decimal(String header) { return new Column(header, ColumnType.DECIMAL); }
        public static Column money(String header) { return new Column(header, ColumnType.MONEY); }
        public static Column percent(String header) { return new Column(header, ColumnType.PERCENT); }
        public static Column datetime(String header) { return new Column(header, ColumnType.DATETIME); }
        public static Column date(String header) { return new Column(header, ColumnType.DATE); }
    }

    private final XSSFWorkbook book = new XSSFWorkbook();
    private final AtomicInteger tableSeq = new AtomicInteger(1);
    private final CellStyle headerStyle;
    private final CellStyle textStyle;
    private final CellStyle intStyle;
    private final CellStyle decimalStyle;
    private final CellStyle moneyStyle;
    private final CellStyle percentStyle;
    private final CellStyle datetimeStyle;
    private final CellStyle dateStyle;
    private final CellStyle emptyStyle;
    private final List<String> usedSheetNames = new ArrayList<>();

    public MerchantExcelWorkbook() {
        headerStyle = buildHeaderStyle();
        textStyle = buildBase(HorizontalAlignment.LEFT, false);
        intStyle = buildBase(HorizontalAlignment.RIGHT, false);
        intStyle.setDataFormat(book.createDataFormat().getFormat("0"));
        decimalStyle = buildBase(HorizontalAlignment.RIGHT, false);
        decimalStyle.setDataFormat(book.createDataFormat().getFormat("#,##0.##"));
        moneyStyle = buildBase(HorizontalAlignment.RIGHT, false);
        moneyStyle.setDataFormat(book.createDataFormat().getFormat("#,##0.00"));
        percentStyle = buildBase(HorizontalAlignment.RIGHT, false);
        percentStyle.setDataFormat(book.createDataFormat().getFormat("0.00"));
        datetimeStyle = buildBase(HorizontalAlignment.LEFT, false);
        datetimeStyle.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
        dateStyle = buildBase(HorizontalAlignment.LEFT, false);
        dateStyle.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd"));
        emptyStyle = buildBase(HorizontalAlignment.LEFT, true);
    }

    /** Meta / context sheet (report title, shop, filters, timezone). Not an Excel Table. */
    public void addMetaSheet(String sheetName, LinkedHashMap<String, String> fields) {
        XSSFSheet sheet = book.createSheet(safeSheetName(sheetName));
        int r = 0;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            Row row = sheet.createRow(r++);
            Cell k = row.createCell(0);
            k.setCellValue(safeText(e.getKey()));
            k.setCellStyle(headerStyle);
            Cell v = row.createCell(1);
            v.setCellValue(safeText(e.getValue() == null ? "" : e.getValue()));
            v.setCellStyle(textStyle);
        }
        sheet.setColumnWidth(0, 28 * 256);
        sheet.setColumnWidth(1, Math.min(MAX_COL_WIDTH, 60 * 256));
        sheet.createFreezePane(0, 1);
    }

    /**
     * Data sheet with a real Excel Table. {@code rows} values align with {@code columns}; null = blank.
     * When rows is empty, still creates a one-row header table and a note under it.
     */
    public void addTableSheet(String sheetName, String tableName, List<Column> columns, List<List<Object>> rows) {
        addTableSheet(sheetName, tableName, columns, rows, null);
    }

    /**
     * Same as {@link #addTableSheet(String, String, List, List)} with optional Excel cell comments
     * on header cells (keyed by exact header string). Does not change header text.
     */
    public void addTableSheet(String sheetName, String tableName, List<Column> columns, List<List<Object>> rows,
                              Map<String, String> headerNotes) {
        if (columns == null || columns.isEmpty()) throw new IllegalArgumentException("columns required");
        XSSFSheet sheet = book.createSheet(safeSheetName(sheetName));
        Row header = sheet.createRow(0);
        var drawing = headerNotes == null || headerNotes.isEmpty() ? null : sheet.createDrawingPatriarch();
        var helper = drawing == null ? null : book.getCreationHelper();
        for (int c = 0; c < columns.size(); c++) {
            Cell cell = header.createCell(c);
            String headerName = columns.get(c).header();
            cell.setCellValue(headerName);
            cell.setCellStyle(headerStyle);
            if (drawing != null && helper != null && headerNotes != null) {
                String note = headerNotes.get(headerName);
                if (note != null && !note.isBlank()) {
                    var anchor = helper.createClientAnchor();
                    anchor.setCol1(c);
                    anchor.setCol2(Math.min(c + 2, columns.size()));
                    anchor.setRow1(0);
                    anchor.setRow2(3);
                    var comment = drawing.createCellComment(anchor);
                    comment.setString(helper.createRichTextString(note));
                    cell.setCellComment(comment);
                }
            }
        }

        List<List<Object>> data = rows == null ? List.of() : rows;
        int lastDataRow = Math.max(1, data.size()); // table needs at least header+1 when empty → add blank row
        if (data.isEmpty()) {
            Row blank = sheet.createRow(1);
            for (int c = 0; c < columns.size(); c++) {
                Cell cell = blank.createCell(c);
                cell.setBlank();
                cell.setCellStyle(styleFor(columns.get(c).type()));
            }
            Row note = sheet.createRow(3);
            Cell n = note.createCell(0);
            n.setCellValue("No data / ไม่พบข้อมูล");
            n.setCellStyle(emptyStyle);
        } else {
            for (int i = 0; i < data.size(); i++) {
                Row row = sheet.createRow(i + 1);
                List<Object> values = data.get(i);
                for (int c = 0; c < columns.size(); c++) {
                    Object value = c < values.size() ? values.get(c) : null;
                    writeCell(row, c, columns.get(c).type(), value);
                }
            }
            lastDataRow = data.size();
        }

        createTable(sheet, safeTableName(tableName), columns, lastDataRow);
        sheet.createFreezePane(0, 1);
        autosize(sheet, columns.size());
    }

    public byte[] toBytes() {
        try (var out = new ByteArrayOutputStream()) {
            book.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("เขียนไฟล์ Excel ไม่สำเร็จ", e);
        }
    }

    @Override
    public void close() {
        try { book.close(); } catch (IOException ignored) {}
    }

    public static String filename(String base, LocalDate day) {
        String safe = base == null ? "export" : base.replaceAll("[\\\\/:*?\"<>|]+", "-").trim();
        if (safe.isEmpty()) safe = "export";
        if (safe.length() > 80) safe = safe.substring(0, 80);
        return safe + "-" + (day == null ? LocalDate.now(BANGKOK) : day) + ".xlsx";
    }

    public static String formatInstantBangkok(Instant instant) {
        if (instant == null) return "";
        return ZonedDateTime.ofInstant(instant, BANGKOK).toString();
    }

    /** Prefix formula-like text so Excel keeps it as text; never used for numeric cells. */
    public static String safeText(String value) {
        if (value == null || value.isEmpty()) return value == null ? "" : value;
        char c = value.charAt(0);
        if (c == '=' || c == '+' || c == '@' || c == '\t' || c == '\r') return "'" + value;
        // Leading '-' only when it is not a plain negative number written as text by mistake.
        if (c == '-' && !looksLikeNumber(value)) return "'" + value;
        return value;
    }

    private static boolean looksLikeNumber(String value) {
        try {
            new BigDecimal(value.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void writeCell(Row row, int col, ColumnType type, Object value) {
        Cell cell = row.createCell(col);
        cell.setCellStyle(styleFor(type));
        if (value == null) {
            cell.setBlank();
            return;
        }
        switch (type) {
            case INTEGER -> {
                if (value instanceof Number n) cell.setCellValue(n.longValue());
                else cell.setCellValue(Long.parseLong(value.toString()));
            }
            case DECIMAL, MONEY, PERCENT -> {
                double d = value instanceof BigDecimal b ? b.doubleValue()
                    : value instanceof Number n ? n.doubleValue()
                    : Double.parseDouble(value.toString());
                cell.setCellValue(d);
            }
            case DATETIME -> {
                Date d = toDate(value);
                if (d == null) cell.setBlank();
                else cell.setCellValue(d);
            }
            case DATE -> {
                Date d = toDate(value);
                if (d == null) cell.setBlank();
                else cell.setCellValue(d);
            }
            default -> cell.setCellValue(safeText(String.valueOf(value)));
        }
    }

    private static Date toDate(Object value) {
        if (value instanceof Date d) return d;
        if (value instanceof Instant i) return Date.from(i);
        if (value instanceof ZonedDateTime z) return Date.from(z.toInstant());
        if (value instanceof LocalDate ld) return Date.from(ld.atStartOfDay(BANGKOK).toInstant());
        return null;
    }

    private CellStyle styleFor(ColumnType type) {
        return switch (type) {
            case INTEGER -> intStyle;
            case DECIMAL -> decimalStyle;
            case MONEY -> moneyStyle;
            case PERCENT -> percentStyle;
            case DATETIME -> datetimeStyle;
            case DATE -> dateStyle;
            default -> textStyle;
        };
    }

    private void createTable(XSSFSheet sheet, String tableName, List<Column> columns, int lastDataRow) {
        AreaReference area = new AreaReference(
            new CellReference(0, 0),
            new CellReference(lastDataRow, columns.size() - 1),
            SpreadsheetVersion.EXCEL2007
        );
        XSSFTable table = sheet.createTable(area);
        table.setName(tableName);
        table.setDisplayName(tableName);
        CTTable ct = table.getCTTable();
        ct.setId(tableSeq.getAndIncrement());
        ct.setRef(area.formatAsString());
        if (ct.getAutoFilter() == null) ct.addNewAutoFilter();
        ct.getAutoFilter().setRef(area.formatAsString());
        CTTableColumns cols = ct.getTableColumns() == null ? ct.addNewTableColumns() : ct.getTableColumns();
        cols.setCount(columns.size());
        while (cols.sizeOfTableColumnArray() > 0) cols.removeTableColumn(0);
        for (int i = 0; i < columns.size(); i++) {
            CTTableColumn col = cols.addNewTableColumn();
            col.setId(i + 1L);
            col.setName(columns.get(i).header());
        }
        CTTableStyleInfo style = ct.getTableStyleInfo() == null ? ct.addNewTableStyleInfo() : ct.getTableStyleInfo();
        style.setName("TableStyleMedium2");
        style.setShowRowStripes(true);
        style.setShowColumnStripes(false);
    }

    private void autosize(Sheet sheet, int colCount) {
        for (int c = 0; c < colCount; c++) {
            sheet.autoSizeColumn(c);
            int width = sheet.getColumnWidth(c);
            if (width < MIN_COL_WIDTH) sheet.setColumnWidth(c, MIN_COL_WIDTH);
            if (width > MAX_COL_WIDTH) sheet.setColumnWidth(c, MAX_COL_WIDTH);
        }
    }

    private String safeSheetName(String raw) {
        String name = raw == null || raw.isBlank() ? "Sheet" : raw.replaceAll("[\\\\/*?:\\[\\]]", " ").trim();
        if (name.length() > 31) name = name.substring(0, 31);
        String base = name;
        int i = 2;
        while (sheetNameTaken(name)) {
            String suffix = " (" + i++ + ")";
            name = base.substring(0, Math.min(31 - suffix.length(), base.length())) + suffix;
        }
        usedSheetNames.add(name);
        return name;
    }

    private boolean sheetNameTaken(String candidate) {
        for (String existing : usedSheetNames) {
            if (existing.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }

    private String safeTableName(String raw) {
        String name = (raw == null || raw.isBlank() ? "Table" : raw).replaceAll("[^A-Za-z0-9_]", "_");
        if (!Character.isLetter(name.charAt(0))) name = "T_" + name;
        if (name.length() > 40) name = name.substring(0, 40);
        return name + "_" + tableSeq.get();
    }

    private CellStyle buildHeaderStyle() {
        XSSFCellStyle style = book.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 8, (byte) 105, (byte) 239}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        XSSFFont font = book.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setWrapText(true);
        return style;
    }

    private CellStyle buildBase(HorizontalAlignment align, boolean italic) {
        CellStyle style = book.createCellStyle();
        style.setAlignment(align);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        Font font = book.createFont();
        font.setItalic(italic);
        style.setFont(font);
        return style;
    }

    public static byte[] contentDispositionUtf8(String filename) {
        return filename.getBytes(StandardCharsets.UTF_8);
    }

    public static Locale localeOf(String lang) {
        if (lang == null || lang.isBlank()) return Locale.ENGLISH;
        String l = lang.trim().toLowerCase(Locale.ROOT);
        if (l.startsWith("th")) return Locale.forLanguageTag("th");
        if (l.startsWith("zh")) return Locale.SIMPLIFIED_CHINESE;
        return Locale.ENGLISH;
    }
}
