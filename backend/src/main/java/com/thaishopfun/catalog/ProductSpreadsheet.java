package com.thaishopfun.catalog;

import com.thaishopfun.merchant.MerchantStorageService;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ProductSpreadsheet {
    public static final int MAX_ROWS = 500;
    public static final int MAX_IMAGES = 9;
    public static final String[] HEADERS = {
        "nameTh", "nameEn", "nameZh", "descriptionTh", "descriptionEn", "descriptionZh",
        "category", "brand", "skuCode", "barcode", "optionLabel", "price", "compareAtPrice", "weightGrams", "stock", "image",
        "productKey", "productType", "option1Name", "option1Value", "option2Name", "option2Value", "option3Name", "option3Value"
    };
    private static final int IMAGE_COLUMN = 15;
    private static final Set<String> IMAGE_ALIASES = Set.of("images", "imageurl", "imageurls", "รูป", "รูปภาพ", "รูปสินค้า");
    private static final Pattern DISPIMG = Pattern.compile("DISPIMG\\(\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private ProductSpreadsheet() {}

    public record EmbeddedImage(String contentType, byte[] data) {}

    public record SheetRow(
        int row,
        String nameTh,
        String nameEn,
        String nameZh,
        String descriptionTh,
        String descriptionEn,
        String descriptionZh,
        String category,
        String brand,
        String skuCode,
        String barcode,
        String optionLabel,
        BigDecimal price,
        BigDecimal compareAtPrice,
        Integer weightGrams,
        Integer stock,
        String image,
        List<EmbeddedImage> pictures,
        String imageError,
        String productKey,
        String productType,
        String option1Name,
        String option1Value,
        String option2Name,
        String option2Value,
        String option3Name,
        String option3Value
    ) {
        public SheetRow {
            pictures = pictures == null ? List.of() : List.copyOf(pictures);
        }
    }

    public static byte[] write(List<SheetRow> rows) {
        return write(rows, null);
    }

    /**
     * Merchant catalog list export: one row per product (same shape as /merchant/products table).
     * Single Excel Table sheet (no Meta). Import template still uses {@link #write}/{@link #template}.
     */
    public record ListExportRow(
        String nameTh,
        String nameEn,
        String sku,
        String category,
        BigDecimal priceMin,
        BigDecimal priceMax,
        int stock,
        String status,
        java.time.Instant updatedAt
    ) {}

    public static byte[] writeExport(List<ListExportRow> rows, Locale locale) {
        int lang = localeLang(locale);
        try (var book = new com.thaishopfun.common.excel.MerchantExcelWorkbook()) {
            var columns = java.util.List.of(
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.text(listHeader(lang, "nameTh")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.text(listHeader(lang, "nameEn")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.text(listHeader(lang, "sku")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.text(listHeader(lang, "category")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.money(listHeader(lang, "price")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.money(listHeader(lang, "priceMax")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.integer(listHeader(lang, "stock")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.text(listHeader(lang, "status")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.datetime(listHeader(lang, "updated"))
            );
            var data = new java.util.ArrayList<java.util.List<Object>>();
            if (rows != null) {
                for (var source : rows) {
                    var row = new java.util.ArrayList<Object>(9);
                    row.add(nz(source.nameTh()));
                    row.add(nz(source.nameEn()));
                    row.add(nz(source.sku()));
                    row.add(nz(source.category()));
                    row.add(source.priceMin());
                    row.add(source.priceMax() != null ? source.priceMax() : source.priceMin());
                    row.add(source.stock());
                    row.add(nz(source.status()));
                    row.add(source.updatedAt());
                    data.add(row);
                }
            }
            book.addTableSheet(sheetName(locale), "Products", columns, data);
            return book.toBytes();
        }
    }

    /** Localized sheet title; keep ≤31 Excel chars. */
    public static String sheetName(Locale locale) {
        Locale loc = locale == null ? Locale.ENGLISH : locale;
        String tag = loc.toLanguageTag().toLowerCase(Locale.ROOT);
        if (tag.startsWith("th")) return "สินค้า";
        if (tag.startsWith("zh")) return "商品";
        return "Products";
    }

    private static int localeLang(Locale locale) {
        if (locale == null) return 0;
        String tag = locale.toLanguageTag().toLowerCase(Locale.ROOT);
        if (tag.startsWith("zh")) return 2;
        if (tag.startsWith("en")) return 1;
        return 0;
    }

    private static String listHeader(int lang, String key) {
        return switch (key) {
            case "nameTh" -> pick(lang, "ชื่อสินค้า (ไทย)", "Product name (TH)", "商品名（泰）");
            case "nameEn" -> pick(lang, "ชื่อสินค้า (EN)", "Product name (EN)", "商品名（英）");
            case "sku" -> pick(lang, "SKU", "SKU", "SKU");
            case "category" -> pick(lang, "หมวดหมู่", "Category", "分类");
            case "price" -> pick(lang, "ราคา", "Price", "价格");
            case "priceMax" -> pick(lang, "ราคาสูงสุด", "Price (max)", "最高价");
            case "stock" -> pick(lang, "สต็อก", "Stock", "库存");
            case "status" -> pick(lang, "สถานะ", "Status", "状态");
            case "updated" -> pick(lang, "แก้ไขล่าสุด", "Updated", "最近修改");
            default -> key;
        };
    }

    private static String pick(int lang, String th, String en, String zh) {
        return lang == 0 ? th : lang == 2 ? zh : en;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    public static byte[] write(List<SheetRow> rows, String readme) {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("products");
            var header = sheet.createRow(0);
            for (var index = 0; index < HEADERS.length; index++) header.createCell(index).setCellValue(HEADERS[index]);
            for (var index = 0; index < rows.size(); index++) {
                var source = rows.get(index);
                var row = sheet.createRow(index + 1);
                writeText(row, 0, source.nameTh());
                writeText(row, 1, source.nameEn());
                writeText(row, 2, source.nameZh());
                writeText(row, 3, source.descriptionTh());
                writeText(row, 4, source.descriptionEn());
                writeText(row, 5, source.descriptionZh());
                writeText(row, 6, source.category());
                writeText(row, 7, source.brand());
                writeText(row, 8, source.skuCode());
                writeText(row, 9, source.barcode());
                writeText(row, 10, source.optionLabel());
                if (source.price() != null) row.createCell(11).setCellValue(source.price().doubleValue());
                if (source.compareAtPrice() != null) row.createCell(12).setCellValue(source.compareAtPrice().doubleValue());
                if (source.weightGrams() != null) row.createCell(13).setCellValue(source.weightGrams());
                if (source.stock() != null) row.createCell(14).setCellValue(source.stock());
                writeText(row, 15, source.image());
                writeText(row, 16, source.productKey());
                writeText(row, 17, source.productType());
                writeText(row, 18, source.option1Name());
                writeText(row, 19, source.option1Value());
                writeText(row, 20, source.option2Name());
                writeText(row, 21, source.option2Value());
                writeText(row, 22, source.option3Name());
                writeText(row, 23, source.option3Value());
            }
            for (var index = 0; index < HEADERS.length; index++) sheet.autoSizeColumn(index);
            if (readme != null && !readme.isBlank()) {
                var note = book.createSheet("readme");
                var lines = readme.split("\n", -1);
                for (var index = 0; index < lines.length; index++) note.createRow(index).createCell(0).setCellValue(lines[index]);
                note.setColumnWidth(0, 120 * 256);
            }
            book.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("เขียนไฟล์ Excel ไม่สำเร็จ", exception);
        }
    }

    public static byte[] template() {
        return write(List.of(
            new SheetRow(2, "เสื้อตัวอย่าง", null, null, "เสื้อหลายตัวเลือก", null, null, null, "ThaiShopFun", "SAMPLE-RED-S", null, "แดง / S", new BigDecimal("390"), new BigDecimal("590"), 280, 8, null, List.of(), null, "SAMPLE-SHIRT", "VARIABLE", "สี", "แดง", "ไซซ์", "S", null, null),
            new SheetRow(3, "ชาไทย", null, null, "ชาไทยหอม", null, null, null, "ThaiShopFun", "TEA-1", null, null, new BigDecimal("45"), null, 100, 5, null, List.of(), null, null, "SIMPLE", null, null, null, null, null, null)
        ), """
            ไฟล์นี้อธิบายคอลัมน์ในชีต products มี 24 คอลัมน์
            nameTh ชื่อสินค้าภาษาไทย บังคับ
            nameEn ชื่อภาษาอังกฤษ ไม่บังคับ ถ้าว่างระบบแปลให้ ถ้ากรอกจะใช้คำนั้น
            nameZh ชื่อภาษาจีน ไม่บังคับ กติกาเดียวกับ nameEn
            descriptionTh คำอธิบายภาษาไทย
            descriptionEn คำอธิบายภาษาอังกฤษ ไม่บังคับ ถ้าว่างระบบแปลให้
            descriptionZh คำอธิบายภาษาจีน ไม่บังคับ กติกาเดียวกับ descriptionEn
            category ชื่อหมวดสินค้า ต้องตรงกับหมวดที่มีในระบบ ว่างได้
            brand แบรนด์ ว่างได้
            skuCode รหัส SKU บังคับ ห้ามซ้ำกับ SKU ที่ยังไม่ถูกลบในร้าน
            barcode บาร์โค้ด ว่างได้
            optionLabel ป้ายตัวเลือก เช่น แดง / S ว่างได้
            price ราคาขาย บังคับ และต้องมากกว่า 0
            compareAtPrice ราคาเดิม ว่างได้ ถ้ากรอกต้องมากกว่าราคาขาย
            weightGrams น้ำหนักกรัม ว่างได้
            stock สต็อกเริ่มต้นของแถวนี้ ว่างได้
            image ลิงก์รูป หรือวางรูป JPG PNG WEBP ในแถว ได้ไม่เกิน 9 รูปต่อสินค้า
            productKey รหัสกลุ่มสินค้า ถ้าว่างแต่ละแถวเป็นสินค้าคนละตัวและไม่ถูกรวมกลุ่ม ถ้าใส่ค่าเดียวกันหลายแถว แถวเหล่านั้นเป็นสินค้าตัวเดียวกัน หนึ่งแถวต่อหนึ่ง SKU
            productType SIMPLE หรือ VARIABLE ถ้าว่าง ระบบถือเป็น SIMPLE เมื่อ productKey ว่างและไม่มีตัวเลือก และถือเป็น VARIABLE เมื่อมี productKey หรือมีคอลัมน์ตัวเลือก
            option1Name ชื่อกลุ่มตัวเลือกที่ 1 เช่น สี ใช้กับสินค้า VARIABLE
            option1Value ค่าตัวเลือกที่ 1 เช่น แดง ทุกค่าต้องมี SKU ที่เปิดขายอย่างน้อย 1 แถว
            option2Name ชื่อกลุ่มตัวเลือกที่ 2 ว่างได้
            option2Value ค่าตัวเลือกที่ 2 ว่างได้
            option3Name ชื่อกลุ่มตัวเลือกที่ 3 ว่างได้
            option3Value ค่าตัวเลือกที่ 3 ว่างได้
            ตัวอย่างแถวแรกเป็นสินค้า VARIABLE หนึ่งตัว แถวที่สองเป็นสินค้า SIMPLE หนึ่งตัว
            """);
    }

    public static List<SheetRow> read(InputStream input) {
        try (var book = new XSSFWorkbook(input)) {
            var sheet = book.getNumberOfSheets() == 0 ? null : book.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) return List.of();
            var formatter = new DataFormatter();
            var header = sheet.getRow(sheet.getFirstRowNum());
            var columns = headerIndex(header, formatter);
            if (columns[0] < 0 || columns[8] < 0 || columns[11] < 0) {
                throw new IllegalArgumentException("ไฟล์ต้องมีคอลัมน์ nameTh, skuCode และ price");
            }
            var anchored = anchoredPictures(sheet);
            var placed = cellImages(book);
            var rows = new ArrayList<SheetRow>();
            for (var index = header.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                var row = sheet.getRow(index);
                if (row == null || blank(text(row, columns[0], formatter)) && blank(text(row, columns[8], formatter))) continue;
                var pictures = new ArrayList<EmbeddedImage>();
                var imageError = anchored.errors().get(index);
                for (var picture : anchored.byRow().getOrDefault(index, List.of())) addPicture(pictures, picture);
                for (var id : dispImgIds(row)) {
                    var found = placed.get(id);
                    if (found == null) imageError = "อ่านรูปที่วางในเซลล์ไม่สำเร็จ ให้ใช้รูป JPG, PNG หรือ WEBP";
                    else addPicture(pictures, found);
                }
                if (pictures.size() > MAX_IMAGES) imageError = "รูปได้ไม่เกิน 9 รูปต่อสินค้า";
                rows.add(new SheetRow(
                    index + 1,
                    text(row, columns[0], formatter),
                    text(row, columns[1], formatter),
                    text(row, columns[2], formatter),
                    text(row, columns[3], formatter),
                    text(row, columns[4], formatter),
                    text(row, columns[5], formatter),
                    text(row, columns[6], formatter),
                    text(row, columns[7], formatter),
                    text(row, columns[8], formatter),
                    text(row, columns[9], formatter),
                    text(row, columns[10], formatter),
                    money(text(row, columns[11], formatter)),
                    money(text(row, columns[12], formatter)),
                    integer(text(row, columns[13], formatter)),
                    integer(text(row, columns[14], formatter)),
                    text(row, columns[IMAGE_COLUMN], formatter),
                    imageError == null ? pictures : List.of(),
                    imageError,
                    text(row, columns[16], formatter),
                    text(row, columns[17], formatter),
                    text(row, columns[18], formatter),
                    text(row, columns[19], formatter),
                    text(row, columns[20], formatter),
                    text(row, columns[21], formatter),
                    text(row, columns[22], formatter),
                    text(row, columns[23], formatter)
                ));
                if (rows.size() > MAX_ROWS) {
                    throw new IllegalArgumentException("นำเข้าได้ไม่เกิน " + MAX_ROWS + " แถวต่อครั้ง");
                }
            }
            return rows;
        } catch (IOException exception) {
            throw new IllegalArgumentException("อ่านไฟล์ Excel ไม่ได้");
        }
    }

    private record PictureScan(Map<Integer, List<EmbeddedImage>> byRow, Map<Integer, String> errors) {}

    private static PictureScan anchoredPictures(XSSFSheet sheet) {
        var byRow = new HashMap<Integer, List<EmbeddedImage>>();
        var errors = new HashMap<Integer, String>();
        var drawing = sheet.getDrawingPatriarch();
        if (drawing == null) return new PictureScan(byRow, errors);
        for (var shape : drawing.getShapes()) {
            if (!(shape instanceof XSSFPicture picture) || !(picture.getAnchor() instanceof XSSFClientAnchor anchor)) continue;
            var data = picture.getPictureData() == null ? null : picture.getPictureData().getData();
            var accepted = acceptPicture(data);
            if (accepted.error() != null) errors.putIfAbsent(anchor.getRow1(), accepted.error());
            else byRow.computeIfAbsent(anchor.getRow1(), key -> new ArrayList<>()).add(accepted.image());
        }
        return new PictureScan(byRow, errors);
    }

    private static Map<String, EmbeddedImage> cellImages(XSSFWorkbook book) {
        var found = new HashMap<String, EmbeddedImage>();
        try {
        PackagePart part = null;
        for (var candidate : book.getPackage().getParts()) {
            if (candidate.getPartName().getName().toLowerCase(Locale.ROOT).endsWith("/cellimages.xml")) {
                part = candidate;
                break;
            }
        }
        if (part == null) return found;
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            var document = factory.newDocumentBuilder().parse(part.getInputStream());
            var nodes = document.getElementsByTagName("*");
            for (var index = 0; index < nodes.getLength(); index++) {
                if (!(nodes.item(index) instanceof Element element) || !"cellImage".equals(element.getLocalName())) continue;
                var name = descendantAttr(element, "name");
                var embed = descendantAttr(element, "embed");
                if (name == null || embed == null) continue;
                var relation = part.getRelationship(embed);
                if (relation == null) continue;
                var media = part.getRelatedPart(relation);
                var accepted = acceptPicture(media.getInputStream().readAllBytes());
                if (accepted.image() != null) found.put(name, accepted.image());
            }
        } catch (Exception ignored) {
            return Map.of();
        }
        return found;
    }

    private record Accepted(EmbeddedImage image, String error) {}

    private static Accepted acceptPicture(byte[] data) {
        if (data == null || data.length == 0) return new Accepted(null, "อ่านรูปในไฟล์ Excel ไม่ได้");
        if (data.length > MAX_IMAGE_BYTES) return new Accepted(null, "รูปต้องมีขนาดไม่เกิน 5 MB");
        var contentType = MerchantStorageService.sniffedImageType(data);
        if (contentType == null) return new Accepted(null, "รองรับเฉพาะรูป JPG, PNG หรือ WEBP");
        return new Accepted(new EmbeddedImage(contentType, Arrays.copyOf(data, data.length)), null);
    }

    private static void addPicture(List<EmbeddedImage> pictures, EmbeddedImage image) {
        for (var existing : pictures) {
            if (Arrays.equals(existing.data(), image.data())) return;
        }
        pictures.add(image);
    }

    private static List<String> dispImgIds(Row row) {
        var ids = new ArrayList<String>();
        if (row == null) return ids;
        for (var cell : row) {
            String raw = null;
            if (cell.getCellType() == CellType.FORMULA) raw = cell.getCellFormula();
            else if (cell.getCellType() == CellType.STRING) raw = cell.getStringCellValue();
            if (raw == null) continue;
            var matcher = DISPIMG.matcher(raw);
            while (matcher.find()) ids.add(matcher.group(1));
        }
        return ids;
    }

    private static String descendantAttr(Element element, String localName) {
        var nodes = element.getElementsByTagName("*");
        for (var index = 0; index < nodes.getLength(); index++) {
            if (!(nodes.item(index) instanceof Element child)) continue;
            var value = child.getAttribute(localName);
            if (value != null && !value.isBlank()) return value;
            var attributes = child.getAttributes();
            for (var cursor = 0; cursor < attributes.getLength(); cursor++) {
                var item = attributes.item(cursor);
                var name = item.getLocalName() == null ? item.getNodeName() : item.getLocalName();
                if (localName.equals(name) && item.getNodeValue() != null && !item.getNodeValue().isBlank()) return item.getNodeValue();
            }
        }
        return null;
    }

    private static int[] headerIndex(Row header, DataFormatter formatter) {
        var columns = new int[HEADERS.length];
        java.util.Arrays.fill(columns, -1);
        if (header == null) return columns;
        for (var cell = header.getFirstCellNum(); cell < header.getLastCellNum(); cell++) {
            var name = text(header, cell, formatter);
            if (name == null) continue;
            for (var index = 0; index < HEADERS.length; index++) {
                if (HEADERS[index].equalsIgnoreCase(name)) columns[index] = cell;
            }
            if (IMAGE_ALIASES.contains(name.toLowerCase(Locale.ROOT))) columns[IMAGE_COLUMN] = cell;
        }
        return columns;
    }

    private static void writeText(Row row, int index, String value) {
        if (value != null && !value.isBlank()) row.createCell(index).setCellValue(value);
    }

    private static String text(Row row, int index, DataFormatter formatter) {
        if (row == null || index < 0) return null;
        var cell = row.getCell(index);
        if (cell == null) return null;
        var value = formatter.formatCellValue(cell).trim();
        return value.isEmpty() ? null : value;
    }

    private static BigDecimal money(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer integer(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.replace(",", "").trim()).intValue();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
