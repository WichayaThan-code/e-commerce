package com.thaishopfun.common;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Thai text for validation codes. Bean Validation annotations on request DTOs carry a code as their message
 * (e.g. {@code @NotBlank(message = "NAME_REQUIRED")}); the code is returned as {@code fieldErrors[].code} and the
 * Thai text below as {@code fieldErrors[].message}. Annotations that carry plain Thai text keep working:
 * they get the generic code {@code INVALID}.
 */
public final class ValidationMessages {
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]+$");
    private static final Map<String, String> TEXT = Map.ofEntries(
        Map.entry("NAME_REQUIRED", "กรุณาระบุชื่อสินค้า (ภาษาไทย)"),
        Map.entry("NAME_TOO_LONG", "ชื่อสินค้ายาวเกิน 255 ตัวอักษร"),
        Map.entry("PRODUCT_TYPE_INVALID", "ประเภทสินค้าไม่ถูกต้อง"),
        Map.entry("SLUG_INVALID", "ชื่อลิงก์ใช้ได้เฉพาะตัวอักษรภาษาอังกฤษ ตัวเลข และขีด"),
        Map.entry("BRAND_TOO_LONG", "ชื่อแบรนด์ยาวเกิน 100 ตัวอักษร"),
        Map.entry("CURRENCY_INVALID", "สกุลเงินต้องมี 3 ตัวอักษร"),
        Map.entry("UNKNOWN_CATEGORY", "หมวดหมู่สินค้าไม่ถูกต้อง"),
        Map.entry("CATEGORY_INACTIVE", "หมวดหมู่นี้ปิดใช้งานแล้ว กรุณาเลือกหมวดอื่น"),
        Map.entry("PRICE_REQUIRED", "กรุณาระบุราคา"),
        Map.entry("PRICE_INVALID", "ราคาต้องมากกว่า 0"),
        Map.entry("COST_PRICE_INVALID", "ต้นทุนต้องไม่ติดลบ"),
        Map.entry("COMPARE_PRICE_INVALID", "ราคาเดิมต้องไม่น้อยกว่าราคาขาย"),
        Map.entry("WEIGHT_INVALID", "น้ำหนักต้องเป็นจำนวนเต็มตั้งแต่ 0 ถึง 1,000,000 กรัม"),
        Map.entry("SIMPLE_SKU_COUNT", "สินค้าเดี่ยวต้องมี 1 รหัส SKU"),
        Map.entry("SKU_REQUIRED", "ต้องมีอย่างน้อย 1 รหัส SKU"),
        Map.entry("SKU_LIMIT", "สินค้าหนึ่งรายการมีได้ไม่เกิน 100 รหัส SKU"),
        Map.entry("SKU_NOT_FOUND", "ไม่พบรหัส SKU นี้ในสินค้า"),
        Map.entry("SKU_CODE_REQUIRED", "กรุณาระบุรหัส SKU"),
        Map.entry("SKU_CODE_TOO_LONG", "รหัส SKU ยาวเกิน 64 ตัวอักษร"),
        Map.entry("SKU_CODE_DUPLICATE_IN_FORM", "รหัส SKU ซ้ำกันในสินค้านี้"),
        Map.entry("SKU_CODE_DUPLICATE", "รหัส SKU ซ้ำกันในสินค้านี้"),
        Map.entry("SKU_CODE_TAKEN", "รหัส SKU นี้มีอยู่แล้วในร้าน"),
        Map.entry("BARCODE_INVALID", "บาร์โค้ดใช้ได้เฉพาะตัวอักษรภาษาอังกฤษ ตัวเลข และขีด ไม่เกิน 64 ตัว"),
        Map.entry("BARCODE_DUPLICATE", "บาร์โค้ดซ้ำกันในสินค้านี้"),
        Map.entry("BARCODE_TAKEN", "บาร์โค้ดนี้มีอยู่แล้วในร้าน"),
        Map.entry("OPTION_LABEL_TOO_LONG", "ชื่อตัวเลือกยาวเกิน 255 ตัวอักษร"),
        Map.entry("STOCK_INVALID", "สต็อกเริ่มต้นต้องเป็นจำนวนเต็มตั้งแต่ 0 ถึง 1,000,000"),
        Map.entry("PUBLISH_IMAGE_REQUIRED", "อัปโหลดรูปสินค้าอย่างน้อย 1 รูปก่อนเผยแพร่"),
        Map.entry("PUBLISH_PRICE_REQUIRED", "ต้องมีราคาก่อนเผยแพร่"),
        Map.entry("PUBLISH_SKU_REQUIRED", "ต้องมีรหัส SKU ที่เปิดขายอย่างน้อย 1 รายการก่อนเผยแพร่"),
        Map.entry("OPTION_GROUP_REQUIRED", "สินค้าแบบตัวเลือกต้องมีกลุ่มตัวเลือก 1-3 กลุ่ม"),
        Map.entry("OPTION_GROUP_LIMIT", "มีกลุ่มตัวเลือกได้ไม่เกิน 3 กลุ่ม"),
        Map.entry("OPTION_GROUPS_NOT_ALLOWED", "สินค้าเดี่ยวไม่มีกลุ่มตัวเลือก"),
        Map.entry("OPTION_GROUP_NAME_REQUIRED", "กรุณาระบุชื่อกลุ่มตัวเลือก"),
        Map.entry("OPTION_GROUP_NAME_TOO_LONG", "ชื่อกลุ่มตัวเลือกยาวเกิน 50 ตัวอักษร"),
        Map.entry("OPTION_GROUP_DUPLICATE", "ชื่อกลุ่มตัวเลือกซ้ำกัน"),
        Map.entry("OPTION_GROUP_NOT_FOUND", "ไม่พบกลุ่มตัวเลือกนี้ในสินค้า"),
        Map.entry("OPTION_GROUP_UNKNOWN", "ไม่มีกลุ่มตัวเลือกนี้ในสินค้า"),
        Map.entry("OPTION_VALUE_REQUIRED", "กรุณาระบุค่าตัวเลือก"),
        Map.entry("OPTION_VALUE_TOO_LONG", "ค่าตัวเลือกยาวเกิน 50 ตัวอักษร"),
        Map.entry("OPTION_VALUE_LIMIT", "กลุ่มตัวเลือกหนึ่งมีค่าได้ไม่เกิน 50 ค่า"),
        Map.entry("OPTION_VALUE_DUPLICATE", "ค่าตัวเลือกซ้ำกันในกลุ่มเดียวกัน"),
        Map.entry("OPTION_VALUE_NOT_FOUND", "ไม่พบค่าตัวเลือกนี้ในสินค้า"),
        Map.entry("OPTION_VALUE_UNKNOWN", "ค่าตัวเลือกนี้ไม่มีในกลุ่มตัวเลือก"),
        Map.entry("OPTION_VALUE_IN_USE", "ลบค่าตัวเลือกนี้ไม่ได้ เพราะยังมี SKU ที่ใช้อยู่ ให้ลบหรือเปลี่ยน SKU นั้นก่อน"),
        Map.entry("VARIANT_OPTION_REQUIRED", "ต้องเลือกค่าตัวเลือกให้ครบทุกกลุ่ม"),
        Map.entry("VARIANT_COMBINATION_DUPLICATE", "มี SKU ที่ใช้ตัวเลือกชุดนี้แล้ว"),
        Map.entry("VARIANT_IMAGE_INVALID", "รูปของตัวเลือกต้องเป็นรูปของสินค้านี้")
    );

    private ValidationMessages() {}

    public static boolean isCode(String value) {
        return value != null && CODE.matcher(value).matches();
    }

    public static String text(String code) {
        return TEXT.getOrDefault(code, "ข้อมูลไม่ถูกต้อง");
    }

    public static FieldViolation violation(String field, String code) {
        return new FieldViolation(field, code, text(code));
    }

    /** Bean Validation property path such as {@code skus[0].price}; the merchant form maps {@code skus[i].field}. */
    public static String fieldPath(String propertyPath) {
        if (propertyPath == null) return "";
        return propertyPath.replaceAll("\\.<[^>]+>", "");
    }

    /** Field violation from a Bean Validation message that is either a code or plain text. */
    public static FieldViolation fromConstraint(String propertyPath, String messageOrCode) {
        var field = fieldPath(propertyPath);
        if (isCode(messageOrCode)) return new FieldViolation(field, messageOrCode, text(messageOrCode));
        return new FieldViolation(field, "INVALID", messageOrCode == null ? "ข้อมูลไม่ถูกต้อง" : messageOrCode);
    }
}
