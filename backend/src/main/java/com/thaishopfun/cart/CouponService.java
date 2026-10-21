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

    private Coupon owned(Shop shop, UUID couponId) {
        return coupons.findByIdAndShopId(couponId, shop.getUuid())
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "ไม่พบคูปอง"));
    }

    private Shop shop(Long shopNo) {
        return shops.findByShopId(shopNo).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้านี้"));
    }
}
