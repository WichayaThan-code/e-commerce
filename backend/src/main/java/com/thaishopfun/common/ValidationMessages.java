package com.thaishopfun.common;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Thai text for validation codes. Bean Validation annotations on request DTOs carry a code as their message
 * (e.g. {@code @NotBlank(message = "NAME_REQUIRED")}); the code is returned as {@code fieldErrors[].code} and the
 * Thai text below as {@code fieldErrors[].message}. Field paths use brackets:
 * {@code skus[0].skuCode}, {@code optionGroups[0].name}, {@code optionGroups[0].values[1].value},
 * {@code optionSchema[0].name}, {@code optionSchema[0].values[1].value}.
 * Annotations that carry plain Thai text keep working: they get the generic code {@code INVALID}.
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
        Map.entry("SAFETY_STOCK_INVALID", "Safety Stock ต้องเป็นจำนวนเต็มตั้งแต่ 0 ถึง 1,000,000"),
        Map.entry("PUBLISH_IMAGE_REQUIRED", "อัปโหลดรูปสินค้าอย่างน้อย 1 รูปก่อนเผยแพร่"),
        Map.entry("PUBLISH_PRICE_REQUIRED", "ต้องมีราคาก่อนเผยแพร่"),
        Map.entry("PUBLISH_SKU_REQUIRED", "ต้องมีรหัส SKU ที่เปิดขายอย่างน้อย 1 รายการก่อนเผยแพร่"),
        Map.entry("PUBLISHED_SKU_REQUIRED", "สินค้าที่เผยแพร่อยู่ต้องมี SKU ที่เปิดขายอย่างน้อย 1 รายการ"),
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
        Map.entry("OPTION_VALUE_UNUSED", "ค่าตัวเลือกนี้ยังไม่มี SKU ที่เปิดขาย"),
        Map.entry("VARIANT_OPTION_REQUIRED", "ต้องเลือกค่าตัวเลือกให้ครบทุกกลุ่ม"),
        Map.entry("VARIANT_COMBINATION_DUPLICATE", "มี SKU ที่ใช้ตัวเลือกชุดนี้แล้ว"),
        Map.entry("VARIANT_IMAGE_INVALID", "รูปของตัวเลือกต้องเป็นรูปของสินค้านี้"),
        Map.entry("THEME_TEMPLATE_INVALID", "เทมเพลตธีมไม่ถูกต้อง"),
        Map.entry("THEME_CONFIG_REQUIRED", "กรุณาระบุค่าธีม"),
        Map.entry("THEME_COLORS_REQUIRED", "กรุณาระบุสีธีม"),
        Map.entry("THEME_COLOR_INVALID", "สีต้องเป็นรหัส #RRGGBB"),
        Map.entry("THEME_FONT_INVALID", "ฟอนต์ที่เลือกไม่รองรับ"),
        Map.entry("THEME_HERO_REQUIRED", "กรุณาระบุข้อมูล Hero"),
        Map.entry("THEME_HERO_LAYOUT_INVALID", "รูปแบบ Hero ต้องเป็น FULL, SPLIT, POSTER, STRIP หรือ MEDIA_FULL"),
        Map.entry("THEME_HERO_TITLE_TOO_LONG", "หัวข้อ Hero ยาวเกิน 120 ตัวอักษร"),
        Map.entry("THEME_HERO_SUBTITLE_TOO_LONG", "ข้อความรอง Hero ยาวเกิน 240 ตัวอักษร"),
        Map.entry("THEME_HERO_CTA_TOO_LONG", "ข้อความปุ่ม Hero ยาวเกิน 120 ตัวอักษร"),
        Map.entry("THEME_HERO_EYEBROW_TOO_LONG", "ข้อความเหนือหัวข้อ Hero ยาวเกิน 60 ตัวอักษร"),
        Map.entry("THEME_HERO_TEXT_INVALID", "ข้อความ Hero ต้องเป็นข้อความธรรมดา ห้ามใส่ HTML"),
        Map.entry("THEME_INTRO_TEXT_INVALID", "\u0e02\u0e49\u0e2d\u0e04\u0e27\u0e32\u0e21\u0e40\u0e23\u0e37\u0e48\u0e2d\u0e07\u0e23\u0e32\u0e27\u0e23\u0e49\u0e32\u0e19\u0e22\u0e32\u0e27\u0e40\u0e01\u0e34\u0e19\u0e01\u0e33\u0e2b\u0e19\u0e14\u0e2b\u0e23\u0e37\u0e2d\u0e21\u0e35 HTML"),
        Map.entry("THEME_HERO_BADGES_INVALID", "\u0e2a\u0e15\u0e34\u0e01\u0e40\u0e01\u0e2d\u0e23\u0e4c\u0e2b\u0e19\u0e49\u0e32\u0e41\u0e23\u0e01\u0e44\u0e14\u0e49\u0e2a\u0e39\u0e07\u0e2a\u0e38\u0e14 3 \u0e2d\u0e31\u0e19 \u0e2d\u0e31\u0e19\u0e25\u0e30\u0e44\u0e21\u0e48\u0e40\u0e01\u0e34\u0e19 24 \u0e15\u0e31\u0e27\u0e2d\u0e31\u0e01\u0e29\u0e23 \u0e41\u0e25\u0e30\u0e2b\u0e49\u0e32\u0e21\u0e21\u0e35 HTML"),
        Map.entry("THEME_TRUST_SUB_INVALID", "\u0e04\u0e33\u0e2d\u0e18\u0e34\u0e1a\u0e32\u0e22\u0e22\u0e48\u0e2d\u0e22\u0e44\u0e21\u0e48\u0e40\u0e01\u0e34\u0e19 80 \u0e15\u0e31\u0e27\u0e2d\u0e31\u0e01\u0e29\u0e23 \u0e41\u0e25\u0e30\u0e2b\u0e49\u0e32\u0e21\u0e21\u0e35 HTML"),
        Map.entry("THEME_ANNOUNCEMENT_INVALID", "\u0e41\u0e16\u0e1a\u0e1b\u0e23\u0e30\u0e01\u0e32\u0e28\u0e44\u0e14\u0e49\u0e2a\u0e39\u0e07\u0e2a\u0e38\u0e14 2 \u0e02\u0e49\u0e2d\u0e04\u0e27\u0e32\u0e21 \u0e02\u0e49\u0e2d\u0e04\u0e27\u0e32\u0e21\u0e25\u0e30\u0e44\u0e21\u0e48\u0e40\u0e01\u0e34\u0e19 120 \u0e15\u0e31\u0e27\u0e2d\u0e31\u0e01\u0e29\u0e23 \u0e41\u0e25\u0e30\u0e2b\u0e49\u0e32\u0e21\u0e21\u0e35 HTML"),
        Map.entry("THEME_INTRO_LINK_INVALID", "\u0e25\u0e34\u0e07\u0e01\u0e4c\u0e40\u0e23\u0e37\u0e48\u0e2d\u0e07\u0e23\u0e32\u0e27\u0e23\u0e49\u0e32\u0e19\u0e15\u0e49\u0e2d\u0e07\u0e40\u0e1b\u0e47\u0e19 path \u0e43\u0e19\u0e23\u0e30\u0e1a\u0e1a\u0e2b\u0e23\u0e37\u0e2d http(s)"),
        Map.entry("THEME_HERO_LINK_INVALID", "ลิงก์ปุ่มต้องเป็น path ในเว็บหรือ https เท่านั้น"),
        Map.entry("THEME_SECTIONS_REQUIRED", "กรุณาระบุรายการ section"),
        Map.entry("THEME_SECTION_INVALID", "ข้อมูล section ไม่ถูกต้อง"),
        Map.entry("THEME_SECTION_KEY_INVALID", "ไม่รู้จัก section นี้"),
        Map.entry("THEME_SECTION_KEY_DUPLICATE", "section ซ้ำกัน"),
        Map.entry("THEME_SECTION_ENABLED_INVALID", "สถานะเปิด/ปิด section ไม่ถูกต้อง"),
        Map.entry("THEME_SECTION_VARIANT_INVALID", "รูปแบบการแสดงผลของ section ไม่ถูกต้อง"),
        Map.entry("THEME_SECTIONS_INCOMPLETE", "ต้องมี section ครบทุกตัว (ปิดได้แต่ห้ามหาย)"),
        Map.entry("THEME_STYLE_RADIUS_INVALID", "รัศมีมุมต้องเป็นตัวเลข 0–32"),
        Map.entry("THEME_STYLE_CARD_INVALID", "รูปแบบการ์ดต้องเป็น FLAT, BORDERED หรือ SHADOW"),
        Map.entry("THEME_STYLE_DENSITY_INVALID", "ความหนาแน่นต้องเป็น COMPACT หรือ COMFORTABLE"),
        Map.entry("THEME_STYLE_BUTTON_INVALID", "รูปทรงปุ่มต้องเป็น ROUNDED, PILL หรือ SQUARE"),
        Map.entry("THEME_BANNERS_INVALID", "รายการแบนเนอร์ไม่ถูกต้อง"),
        Map.entry("THEME_BANNERS_TOO_MANY", "แบนเนอร์ได้สูงสุด 8 ภาพ"),
        Map.entry("THEME_BANNER_INVALID", "ข้อมูลแบนเนอร์ไม่ถูกต้อง"),
        Map.entry("THEME_BANNER_IMAGE_REQUIRED", "กรุณาระบุรูปแบนเนอร์"),
        Map.entry("THEME_BANNER_IMAGE_INVALID", "รูปแบนเนอร์ต้องเป็น path ในเว็บหรือ https"),
        Map.entry("THEME_BANNER_TITLE_TOO_LONG", "ชื่อแบนเนอร์ยาวเกิน 80 ตัวอักษร"),
        Map.entry("THEME_BANNER_LINK_INVALID", "ลิงก์แบนเนอร์ต้องเป็น path ในเว็บหรือ https"),
        Map.entry("THEME_TRUST_ENABLED_INVALID", "สถานะเปิด/ปิดแถบความน่าเชื่อถือไม่ถูกต้อง"),
        Map.entry("THEME_TRUST_ITEMS_INVALID", "รายการแถบความน่าเชื่อถือไม่ถูกต้อง"),
        Map.entry("THEME_TRUST_ITEM_INVALID", "รายการความน่าเชื่อถือไม่ถูกต้อง"),
        Map.entry("THEME_TRUST_LABEL_TOO_LONG", "ข้อความความน่าเชื่อถือยาวเกิน 60 ตัวอักษร"),
        Map.entry("THEME_TRUST_ICON_INVALID", "ไอคอนความน่าเชื่อถือไม่รองรับ"),
        Map.entry("THEME_GRADIENT_INVALID", "ค่าไล่สีไม่ถูกต้อง"),
        Map.entry("THEME_TOKEN_INVALID", "ค่าธีมไม่ถูกต้อง"),
        Map.entry("THEME_BLOCKS_INVALID", "การจัดวางบล็อกไม่ถูกต้อง (รหัสซ้ำ เกิน 30 บล็อก หรือมี HERO/HOURS ซ้ำ)"),
        Map.entry("THEME_BLOCK_CONTENT_INVALID", "เนื้อหาบล็อกไม่ถูกต้อง หรือยาว/มีจำนวนรายการเกินกำหนด"),
        Map.entry("THEME_BLOCK_LINK_INVALID", "ลิงก์ในบล็อกต้องเป็น path ในเว็บหรือ https"),
        Map.entry("THEME_PRODUCT_INVALID", "สินค้านี้ไม่พบ หรือยังไม่เผยแพร่ในร้านนี้"),
        Map.entry("THEME_SOCIAL_INVALID", "วิดีโอโซเชียลไม่ถูกต้อง (รองรับเฉพาะรหัสวิดีโอ TikTok ที่เป็นตัวเลข 10–25 หลัก ห้ามใส่ URL หรือโค้ดฝัง)"),
        Map.entry("THEME_RICHTEXT_INVALID", "ข้อความจัดรูปแบบไม่ถูกต้อง (ยาวเกิน 5,000 ตัวอักษร หรือมี HTML/สคริปต์/ลิงก์ที่ไม่อนุญาต)"),
        Map.entry("THEME_CHROME_INVALID", "ส่วนหัว/ส่วนท้ายร้านไม่ถูกต้อง (เมนูสูงสุด 8 รายการ, คอลัมน์ท้ายเว็บสูงสุด 4 คอลัมน์ คอลัมน์ละ 8 ลิงก์, ต้องมีชื่อและลิงก์ครบ)"),
        Map.entry("THEME_FONT_CUSTOM_INVALID", "ฟอนต์กำหนดเองไม่ถูกต้อง (ต้องเป็นไฟล์ฟอนต์ WOFF2 ที่อัปโหลดไว้ในคลังสื่อของร้านนี้)"),
        Map.entry("THEME_MEDIA_CUSTOM_ONLY", "อัปโหลดวิดีโอและฟอนต์ได้เฉพาะธีมแบบกำหนดเอง (CUSTOM) เท่านั้น"),
        Map.entry("THEME_HERO_SLIDES_INVALID", "รูปสไลด์ Hero ไม่ถูกต้อง"),
        Map.entry("THEME_HERO_SLIDES_TOO_MANY", "รูปสไลด์ Hero ใส่ได้สูงสุด 5 รูป"),
        Map.entry("THEME_MEDIA_QUOTA_EXCEEDED", "คลังสื่อของร้านเต็มแล้ว กรุณาลบไฟล์ที่ไม่ใช้ก่อนอัปโหลดเพิ่ม"),
        Map.entry("THEME_MEDIA_IN_USE", "ไฟล์นี้กำลังถูกใช้งานในธีมของร้าน กรุณานำออกจากธีมก่อนลบ"),
        Map.entry("THEME_MEDIA_INVALID", "ไฟล์ไม่ถูกต้อง รองรับเฉพาะรูป JPG/PNG/WEBP, วิดีโอ MP4 และฟอนต์ WOFF2"),
        Map.entry("THEME_MEDIA_KIND_INVALID", "ประเภทสื่อไม่ถูกต้อง"),
        Map.entry("THEME_MEDIA_NOT_FOUND", "ไม่พบไฟล์สื่อนี้"),
        Map.entry("COLLECTION_NAME_REQUIRED", "กรุณาระบุชื่อคอลเลกชัน (ไม่เกิน 120 ตัวอักษร)"),
        Map.entry("COLLECTION_SLUG_INVALID", "ลิงก์คอลเลกชันใช้ได้เฉพาะ a-z, 0-9 และ - ยาว 1–80 ตัวอักษร"),
        Map.entry("COLLECTION_SLUG_TAKEN", "ลิงก์คอลเลกชันนี้ถูกใช้แล้วในร้านนี้"),
        Map.entry("COLLECTION_DESCRIPTION_TOO_LONG", "คำอธิบายคอลเลกชันยาวเกิน 1,000 ตัวอักษร หรือมี HTML"),
        Map.entry("COLLECTION_LIMIT_REACHED", "จำนวนคอลเลกชันของร้านครบตามที่กำหนดแล้ว"),
        Map.entry("COLLECTION_PRODUCT_LIMIT_REACHED", "จำนวนสินค้าในคอลเลกชันครบตามที่กำหนดแล้ว"),
        Map.entry("COLLECTION_PRODUCT_INVALID", "สินค้านี้ไม่พบในร้านนี้ หรือถูกลบไปแล้ว"),
        Map.entry("COLLECTION_COVER_INVALID", "รูปปกต้องเป็นรูปภาพในคลังสื่อของร้านนี้"),
        Map.entry("COLLECTION_ORDER_INVALID", "ลำดับคอลเลกชัน/สินค้าไม่ถูกต้อง (ต้องมีครบทุกรายการและไม่ซ้ำ)"),
        Map.entry("CONFIGURATOR_SCHEMA_INVALID", "ขั้นตอนตัวออกแบบสินค้าไม่ถูกต้อง (สูงสุด 10 ขั้น ขั้นละ 30 ตัวเลือก รหัสใช้ a-z 0-9 _ - และห้ามซ้ำ)"),
        Map.entry("CONFIGURATOR_PRICE_INVALID", "ราคาเพิ่ม/ลดต้องเป็นตัวเลขทศนิยมไม่เกิน 2 ตำแหน่ง และไม่เกิน ±1,000,000"),
        Map.entry("CONFIGURATOR_MEDIA_INVALID", "รูปตัวเลือกต้องเป็นรูปภาพในคลังสื่อของร้านนี้"),
        Map.entry("CONFIGURATOR_SELECTION_REQUIRED", "กรุณาเลือกขั้นตอนนี้ให้ครบ"),
        Map.entry("CONFIGURATOR_SELECTION_INVALID", "ตัวเลือกไม่ถูกต้อง ไม่มีอยู่ หรือสินค้าหมด"),
        Map.entry("CONFIGURATOR_SELECTION_COUNT", "จำนวนตัวเลือกไม่อยู่ในช่วงที่กำหนด"),
        Map.entry("CONFIGURATOR_TEXT_INVALID", "ข้อความไม่ถูกต้อง (ยาวเกินกำหนด ใช้ตัวอักษรที่ไม่อนุญาต หรือมี HTML)"),
        Map.entry("CONFIGURATOR_VERSION_STALE", "ตัวออกแบบสินค้าถูกแก้ไขแล้ว กรุณาโหลดหน้าใหม่"),
        Map.entry("CONFIGURATOR_TOTAL_NEGATIVE", "ราคารวมหลังปรับต้องไม่ติดลบ")
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
