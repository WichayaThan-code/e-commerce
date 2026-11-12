package com.thaishopfun.cart;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/** Merchant coupon management: each coupon = one coupons row + its own promotions row (V1 tables). */
@Service
public class CouponService {
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_-]{3,32}$");
    public static final int EXPORT_MAX_ROWS = 10_000;

    private final CouponRepository coupons;
    private final PromotionRepository promotions;
    private final PromotionUsageRepository usages;
    private final ShopRepository shops;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public CouponService(CouponRepository coupons, PromotionRepository promotions, PromotionUsageRepository usages, ShopRepository shops,
                         org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.coupons = coupons;
        this.promotions = promotions;
        this.usages = usages;
        this.shops = shops;
        this.jdbc = jdbc;
    }

    private static final Set<String> STATUSES = Set.of("UPCOMING", "ACTIVE", "PAUSED", "EXPIRED");

    @Transactional(readOnly = true)
    public CartDtos.CouponListResponse list(Long shopNo, String q, String state, String status, String type, Integer page, Integer size) {
        var shop = shop(shopNo);
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, 100);
        String wantedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (wantedStatus != null && !STATUSES.contains(wantedStatus)) {
            throw new ValidationException(List.of(new FieldViolation("status", "STATUS_INVALID", "สถานะต้องเป็น UPCOMING, ACTIVE, PAUSED หรือ EXPIRED")));
        }
        Set<String> wantedTypes = new java.util.HashSet<>();
        if (type != null && !type.isBlank()) {
            for (var part : type.split(",")) {
                var db = CartService.toDbPromoType(part);
                if (db == null) throw new ValidationException(List.of(new FieldViolation("type", "TYPE_INVALID", "ประเภทต้องเป็น PERCENT, FIXED หรือ FREE_SHIPPING")));
                wantedTypes.add(CartService.toApiPromoType(db));
            }
        }
        var rows = coupons.findByShopIdOrderByCreatedDateTimeDesc(shop.getUuid()).stream()
            .map(coupon -> toResponse(shop, coupon, promotions.findById(coupon.getPromotionId()).orElse(null)))
            .filter(row -> row != null)
            .toList();
        String needle = q == null ? "" : q.trim();
        var all = rows.stream()
            .filter(row -> needle.isEmpty() || row.code().contains(needle.toUpperCase(Locale.ROOT))
                || (row.name() != null && row.name().toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT))))
            .filter(row -> state == null || state.isBlank() || row.state().equalsIgnoreCase(state.trim()))
            .filter(row -> wantedStatus == null || row.status().equals(wantedStatus))
            .filter(row -> wantedTypes.isEmpty() || wantedTypes.contains(row.type()))
            .toList();
        int from = Math.min(all.size(), p * s);
        int to = Math.min(all.size(), from + s);
        return new CartDtos.CouponListResponse(all.subList(from, to), p, s, all.size(), (int) Math.ceil(all.size() / (double) s), summary(rows));
    }

    private static CartDtos.CouponSummary summary(List<CartDtos.CouponResponse> rows) {
        var byStatus = rows.stream().collect(Collectors.groupingBy(CartDtos.CouponResponse::status, Collectors.counting()));
        var byType = rows.stream().collect(Collectors.groupingBy(CartDtos.CouponResponse::type, Collectors.counting()));
        var discount = rows.stream().map(CartDtos.CouponResponse::discountTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartDtos.CouponSummary(rows.size(), byStatus.getOrDefault("ACTIVE", 0L), byStatus.getOrDefault("UPCOMING", 0L),
            byStatus.getOrDefault("PAUSED", 0L), byStatus.getOrDefault("EXPIRED", 0L),
            byType.getOrDefault("PERCENT", 0L), byType.getOrDefault("FIXED", 0L), byType.getOrDefault("FREE_SHIPPING", 0L),
            rows.stream().mapToLong(CartDtos.CouponResponse::usedCount).sum(), CartMoney.of(discount));
    }

    /** Hard delete (coupon + its promotion; wallet rows cascade). A coupon already used in an order: 409 COUPON_IN_USE (disable it instead). */
    @Transactional
    public void delete(Long shopNo, UUID couponId) {
        var shop = shop(shopNo);
        var coupon = owned(shop, couponId);
        if (usages.countByPromotionId(coupon.getPromotionId()) > 0) {
            throw new AuthException(HttpStatus.CONFLICT, "COUPON_IN_USE", "คูปองนี้ถูกใช้ในคำสั่งซื้อแล้ว ลบไม่ได้ — ใช้ \"ปิดใช้งาน\" แทน");
        }
        jdbc.update("DELETE FROM coupon_collections WHERE coupon_id = ?", coupon.getId());
        var promotionId = coupon.getPromotionId();
        coupons.delete(coupon);
        coupons.flush();
        promotions.findById(promotionId).ifPresent(promotions::delete);
    }

    @Transactional(readOnly = true)
    public CartDtos.CouponResponse get(Long shopNo, UUID couponId) {
        var shop = shop(shopNo);
        var coupon = owned(shop, couponId);
        return toResponse(shop, coupon, promotions.findById(coupon.getPromotionId()).orElseThrow());
    }

    @Transactional
    public CartDtos.CouponResponse create(UUID userId, Long shopNo, CartDtos.CouponRequest request) {
        var shop = shop(shopNo);
        var clean = validate(request, true);
        if (coupons.findByShopIdAndCode(shop.getUuid(), clean.code).isPresent()) {
            throw new ValidationException(HttpStatus.CONFLICT, "COUPON_CODE_DUPLICATE",
                List.of(new FieldViolation("code", "COUPON_CODE_DUPLICATE", "โค้ดนี้มีอยู่แล้วในร้าน")));
        }
        var promo = new Promotion(shop.getUuid());
        apply(promo, clean);
        promo.setPublicVisible(request.showOnStorefront() == null || request.showOnStorefront());
        promo.stamp(userId);
        promotions.save(promo);
        var coupon = new Coupon(shop.getUuid(), promo.getId(), clean.code);
        coupon.setActive(clean.active);
        coupon.stamp(userId);
        coupons.save(coupon);
        return toResponse(shop, coupon, promo);
    }

    @Transactional
    public CartDtos.CouponResponse update(UUID userId, Long shopNo, UUID couponId, CartDtos.CouponRequest request) {
        var shop = shop(shopNo);
        var coupon = owned(shop, couponId);
        var promo = promotions.findById(coupon.getPromotionId()).orElseThrow();
        var clean = validate(request, false);
        if (clean.code != null && !clean.code.equals(coupon.getCode())) {
            if (coupons.existsByShopIdAndCodeAndIdNot(shop.getUuid(), clean.code, coupon.getId())) {
                throw new ValidationException(HttpStatus.CONFLICT, "COUPON_CODE_DUPLICATE",
                    List.of(new FieldViolation("code", "COUPON_CODE_DUPLICATE", "โค้ดนี้มีอยู่แล้วในร้าน")));
            }
            if (usages.countByPromotionId(promo.getId()) > 0) {
                throw new ValidationException(HttpStatus.CONFLICT, "COUPON_IN_USE",
                    List.of(new FieldViolation("code", "COUPON_IN_USE", "คูปองถูกใช้แล้ว เปลี่ยนโค้ดไม่ได้")));
            }
            coupon.setCode(clean.code);
        }
        apply(promo, clean);
        if (request.showOnStorefront() != null) promo.setPublicVisible(request.showOnStorefront());
        coupon.setActive(clean.active);
        promo.stamp(userId);
        coupon.stamp(userId);
        promotions.save(promo);
        coupons.save(coupon);
        return toResponse(shop, coupon, promo);
    }

    @Transactional
    public CartDtos.CouponResponse setActive(UUID userId, Long shopNo, UUID couponId, boolean active) {
        var shop = shop(shopNo);
        var coupon = owned(shop, couponId);
        var promo = promotions.findById(coupon.getPromotionId()).orElseThrow();
        coupon.setActive(active);
        promo.setActive(active);
        coupon.stamp(userId);
        promo.stamp(userId);
        coupons.save(coupon);
        promotions.save(promo);
        return toResponse(shop, coupon, promo);
    }

    private record Clean(String code, String name, String type, BigDecimal value, BigDecimal maxDiscount, BigDecimal minSubtotal,
                         Instant startsAt, Instant endsAt, Integer limitTotal, Integer limitPerCustomer, boolean active) {}

    private Clean validate(CartDtos.CouponRequest request, boolean creating) {
        var errors = new ArrayList<FieldViolation>();
        if (request == null) throw new ValidationException(List.of(new FieldViolation("code", "REQUIRED", "ต้องกรอกโค้ด")));
        String code = request.code() == null ? null : request.code().trim().toUpperCase();
        if (code == null || code.isEmpty()) errors.add(new FieldViolation("code", "REQUIRED", "ต้องกรอกโค้ดคูปอง"));
        else if (!CODE.matcher(code).matches()) errors.add(new FieldViolation("code", "PATTERN", "โค้ดต้องเป็น A-Z 0-9 _ - ยาว 3-32 ตัว"));
        var type = CartService.toDbPromoType(request.type());
        if (type == null) errors.add(new FieldViolation("type", "INVALID", "ประเภทต้องเป็น PERCENT, FIXED หรือ FREE_SHIPPING"));
        BigDecimal value = request.value();
        if ("FREE_SHIPPING".equals(type)) {
            value = value == null ? BigDecimal.ZERO : value;
            if (value.signum() < 0) errors.add(new FieldViolation("value", "MIN", "มูลค่าต้องไม่ติดลบ"));
        } else if (type != null) {
            if (value == null || value.signum() <= 0) errors.add(new FieldViolation("value", "MIN", "มูลค่าต้องมากกว่า 0"));
            else if ("PERCENT".equals(type) && value.compareTo(new BigDecimal("100")) > 0) {
                errors.add(new FieldViolation("value", "MAX", "เปอร์เซ็นต์ต้องไม่เกิน 100"));
            }
        }
        if (value != null && value.scale() > 2) errors.add(new FieldViolation("value", "SCALE", "ทศนิยมได้ไม่เกิน 2 ตำแหน่ง"));
        if (request.maxDiscount() != null && request.maxDiscount().signum() <= 0) {
            errors.add(new FieldViolation("maxDiscount", "MIN", "ส่วนลดสูงสุดต้องมากกว่า 0"));
        }
        if (request.minSubtotal() != null && request.minSubtotal().signum() < 0) {
            errors.add(new FieldViolation("minSubtotal", "MIN", "ยอดขั้นต่ำต้องไม่ติดลบ"));
        }
        var startsAt = request.startsAt() == null ? Instant.now() : request.startsAt();
        if (request.endsAt() != null && !request.endsAt().isAfter(startsAt)) {
            errors.add(new FieldViolation("endsAt", "AFTER_START", "วันสิ้นสุดต้องหลังวันเริ่ม"));
        }
        if (request.usageLimitTotal() != null && request.usageLimitTotal() < 1) {
            errors.add(new FieldViolation("usageLimitTotal", "MIN", "จำนวนสิทธิ์ต้องอย่างน้อย 1"));
        }
        if (request.usageLimitPerCustomer() != null && request.usageLimitPerCustomer() < 1) {
            errors.add(new FieldViolation("usageLimitPerCustomer", "MIN", "สิทธิ์ต่อคนต้องอย่างน้อย 1"));
        }
        if (request.usageLimitTotal() != null && request.usageLimitPerCustomer() != null
            && request.usageLimitPerCustomer() > request.usageLimitTotal()) {
            errors.add(new FieldViolation("usageLimitPerCustomer", "MAX", "สิทธิ์ต่อคนต้องไม่เกินจำนวนสิทธิ์ทั้งหมด"));
        }
        var name = request.name() == null || request.name().isBlank() ? code : request.name().trim();
        if (name != null && name.length() > 150) errors.add(new FieldViolation("name", "MAX_LENGTH", "ชื่อยาวเกิน 150 ตัวอักษร"));
        if (!errors.isEmpty()) throw new ValidationException(ValidationException.topCode(errors), errors);
        return new Clean(code, name, type, value, request.maxDiscount(), request.minSubtotal(), startsAt, request.endsAt(),
            request.usageLimitTotal(), request.usageLimitPerCustomer(), request.active() == null || request.active());
    }

    private static void apply(Promotion promo, Clean clean) {
        promo.setName(clean.name);
        promo.setPromoType(clean.type);
        promo.setValue(CartMoney.of(clean.value));
        promo.setMaxDiscountAmount(clean.maxDiscount == null ? null : CartMoney.of(clean.maxDiscount));
        promo.setMinOrderAmount(clean.minSubtotal == null ? null : CartMoney.of(clean.minSubtotal));
        promo.setStartsAt(clean.startsAt);
        promo.setEndsAt(clean.endsAt);
        promo.setUsageLimitTotal(clean.limitTotal);
        promo.setUsageLimitPerBuyer(clean.limitPerCustomer);
        promo.setActive(clean.active);
    }

    private CartDtos.CouponResponse toResponse(Shop shop, Coupon coupon, Promotion promo) {
        if (promo == null) return null;
        long used = usages.countByPromotionId(promo.getId());
        var now = Instant.now();
        String state;
        if (!coupon.isActive() || !promo.isActive()) state = "DISABLED";
        else if (promo.getEndsAt() != null && !now.isBefore(promo.getEndsAt())) state = "EXPIRED";
        else if (promo.getStartsAt() != null && now.isBefore(promo.getStartsAt())) state = "SCHEDULED";
        else if (promo.getUsageLimitTotal() != null && used >= promo.getUsageLimitTotal()) state = "USED_UP";
        else state = "ACTIVE";
        String status;
        if (promo.getEndsAt() != null && !now.isBefore(promo.getEndsAt())) status = "EXPIRED";
        else if (!coupon.isActive() || !promo.isActive()) status = "PAUSED";
        else if (promo.getStartsAt() != null && now.isBefore(promo.getStartsAt())) status = "UPCOMING";
        else status = "ACTIVE";
        var discountTotal = usages.sumDiscountByPromotionId(promo.getId());
        return new CartDtos.CouponResponse(coupon.getId(), shop.getId(), coupon.getCode(), promo.getName(),
            CartService.toApiPromoType(promo.getPromoType()), CartMoney.of(promo.getValue()),
            promo.getMaxDiscountAmount() == null ? null : CartMoney.of(promo.getMaxDiscountAmount()),
            promo.getMinOrderAmount() == null ? null : CartMoney.of(promo.getMinOrderAmount()),
            promo.getStartsAt(), promo.getEndsAt(), promo.getUsageLimitTotal(), promo.getUsageLimitPerBuyer(),
            used, coupon.isActive() && promo.isActive(), state, coupon.getCreatedDateTime(), coupon.getModifiedDateTime(),
            promo.isPublicVisible(), CartMoney.of(discountTotal == null ? BigDecimal.ZERO : discountTotal), status);
    }

    /**
     * Merchant promotions list export: same filters as {@link #list}, one row per coupon,
     * single Excel Table sheet (no Meta). Matches /merchant/promotions table columns.
     */
    @Transactional(readOnly = true)
    public byte[] exportWorkbook(Long shopNo, String q, String status, String type, Locale locale) {
        var all = new ArrayList<CartDtos.CouponResponse>();
        int page = 0;
        int totalPages;
        do {
            var result = list(shopNo, q, null, status, type, page, 100);
            all.addAll(result.items());
            totalPages = result.totalPages();
            if (all.size() > EXPORT_MAX_ROWS) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "EXPORT_TOO_LARGE",
                    "ส่งออกได้สูงสุด " + EXPORT_MAX_ROWS + " แถว — ลดตัวกรองแล้วลองใหม่");
            }
            page++;
        } while (page < totalPages);

        if (all.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "EXPORT_EMPTY",
                "ไม่มีโปรโมชั่นตามตัวกรองนี้ กรุณาปรับตัวกรองแล้วลองใหม่");
        }

        int lang = localeLang(locale);
        try (var book = new com.thaishopfun.common.excel.MerchantExcelWorkbook()) {
            var columns = java.util.List.of(
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.text(h(lang, "code")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.text(h(lang, "name")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.text(h(lang, "type")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.text(h(lang, "benefit")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.money(h(lang, "minOrder")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.money(h(lang, "maxDiscount")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.datetime(h(lang, "startsAt")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.datetime(h(lang, "endsAt")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.integer(h(lang, "used")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.text(h(lang, "limit")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.text(h(lang, "status")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.text(h(lang, "storefront")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.money(h(lang, "discountTotal")),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.datetime(h(lang, "updated"))
            );
            var data = new ArrayList<java.util.List<Object>>(all.size());
            for (var row : all) {
                var cells = new ArrayList<Object>(14);
                cells.add(nz(row.code()));
                cells.add(nz(row.name()));
                cells.add(typeLabel(lang, row.type()));
                cells.add(benefitLabel(lang, row.type(), row.value()));
                cells.add(row.minSubtotal());
                cells.add(row.maxDiscount());
                cells.add(row.startsAt());
                cells.add(row.endsAt());
                cells.add((int) row.usedCount());
                cells.add(row.usageLimitTotal() == null ? unlimitedLabel(lang) : String.valueOf(row.usageLimitTotal()));
                cells.add(statusLabel(lang, row.status()));
                cells.add(row.showOnStorefront() ? yesLabel(lang) : noLabel(lang));
                cells.add(row.discountTotal());
                cells.add(row.updatedAt());
                data.add(cells);
            }
            book.addTableSheet(h(lang, "sheet"), "Promotions", columns, data);
            return book.toBytes();
        }
    }

    public static String suggestedFilename(Long shopId) {
        return com.thaishopfun.common.excel.MerchantExcelWorkbook.filename(
            "promotions-" + shopId,
            java.time.LocalDate.now(com.thaishopfun.common.excel.MerchantExcelWorkbook.BANGKOK));
    }

    private static int localeLang(Locale locale) {
        if (locale == null) return 0;
        String tag = locale.toLanguageTag().toLowerCase(Locale.ROOT);
        if (tag.startsWith("zh")) return 2;
        if (tag.startsWith("en")) return 1;
        return 0;
    }

    private static String h(int lang, String key) {
        return switch (key) {
            case "sheet" -> pick(lang, "โปรโมชั่น", "Promotions", "促销");
            case "code" -> pick(lang, "รหัสคูปอง", "Code", "优惠码");
            case "name" -> pick(lang, "ชื่อโปรโมชั่น", "Name", "名称");
            case "type" -> pick(lang, "ประเภท", "Type", "类型");
            case "benefit" -> pick(lang, "ส่วนลด", "Benefit", "优惠");
            case "minOrder" -> pick(lang, "ยอดขั้นต่ำ", "Min order", "最低消费");
            case "maxDiscount" -> pick(lang, "ลดสูงสุด", "Max discount", "最高优惠");
            case "startsAt" -> pick(lang, "เริ่มต้น", "Starts", "开始");
            case "endsAt" -> pick(lang, "สิ้นสุด", "Ends", "结束");
            case "used" -> pick(lang, "ใช้แล้ว", "Used", "已用");
            case "limit" -> pick(lang, "สิทธิ์ทั้งหมด", "Limit", "总额度");
            case "status" -> pick(lang, "สถานะ", "Status", "状态");
            case "storefront" -> pick(lang, "แสดงบนหน้าร้าน", "Storefront", "店铺展示");
            case "discountTotal" -> pick(lang, "มูลค่าลดรวม", "Discount total", "优惠合计");
            case "updated" -> pick(lang, "แก้ไขล่าสุด", "Updated", "最近修改");
            default -> key;
        };
    }

    private static String typeLabel(int lang, String type) {
        return switch (type == null ? "" : type) {
            case "PERCENT" -> pick(lang, "ส่วนลด %", "Percent off", "百分比");
            case "FIXED" -> pick(lang, "ส่วนลดบาท", "Fixed amount", "固定金额");
            case "FREE_SHIPPING" -> pick(lang, "ส่งฟรี", "Free shipping", "包邮");
            default -> nz(type);
        };
    }

    private static String benefitLabel(int lang, String type, BigDecimal value) {
        var amount = value == null ? BigDecimal.ZERO : value;
        if ("PERCENT".equals(type)) return pick(lang, "ลด " + amount.stripTrailingZeros().toPlainString() + "%",
            amount.stripTrailingZeros().toPlainString() + "% off", "减 " + amount.stripTrailingZeros().toPlainString() + "%");
        if ("FIXED".equals(type)) return pick(lang, "ลด " + amount.stripTrailingZeros().toPlainString() + " บาท",
            amount.stripTrailingZeros().toPlainString() + " THB off", "减 " + amount.stripTrailingZeros().toPlainString() + " 泰铢");
        return pick(lang, "ฟรีค่าจัดส่ง", "Free shipping", "包邮");
    }

    private static String statusLabel(int lang, String status) {
        return switch (status == null ? "" : status) {
            case "ACTIVE" -> pick(lang, "ใช้งานอยู่", "Active", "进行中");
            case "UPCOMING" -> pick(lang, "ยังไม่เริ่ม", "Upcoming", "未开始");
            case "PAUSED" -> pick(lang, "หยุดชั่วคราว", "Paused", "已暂停");
            case "EXPIRED" -> pick(lang, "หมดอายุ", "Expired", "已过期");
            default -> nz(status);
        };
    }

    private static String unlimitedLabel(int lang) {
        return pick(lang, "ไม่จำกัด", "Unlimited", "不限");
    }

    private static String yesLabel(int lang) {
        return pick(lang, "ใช่", "Yes", "是");
    }

    private static String noLabel(int lang) {
        return pick(lang, "ไม่", "No", "否");
    }

    private static String pick(int lang, String th, String en, String zh) {
        return lang == 0 ? th : lang == 2 ? zh : en;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private Coupon owned(Shop shop, UUID couponId) {
        return coupons.findByIdAndShopId(couponId, shop.getUuid())
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "ไม่พบคูปอง"));
    }

    private Shop shop(Long shopNo) {
        return shops.findByShopId(shopNo).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้านี้"));
    }
}
