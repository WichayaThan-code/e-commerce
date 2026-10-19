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
import java.util.UUID;
import java.util.regex.Pattern;

/** Merchant coupon management: each coupon = one coupons row + its own promotions row (V1 tables). */
@Service
public class CouponService {
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_-]{3,32}$");

    private final CouponRepository coupons;
    private final PromotionRepository promotions;
    private final PromotionUsageRepository usages;
    private final ShopRepository shops;

    public CouponService(CouponRepository coupons, PromotionRepository promotions, PromotionUsageRepository usages, ShopRepository shops) {
        this.coupons = coupons;
        this.promotions = promotions;
        this.usages = usages;
        this.shops = shops;
    }

    @Transactional(readOnly = true)
    public CartDtos.PageResponse<CartDtos.CouponResponse> list(Long shopNo, String q, String state, Integer page, Integer size) {
        var shop = shop(shopNo);
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, 100);
        var all = coupons.findByShopIdOrderByCreatedDateTimeDesc(shop.getUuid()).stream()
            .map(coupon -> toResponse(shop, coupon, promotions.findById(coupon.getPromotionId()).orElse(null)))
            .filter(row -> row != null)
            .filter(row -> q == null || q.isBlank() || row.code().contains(q.trim().toUpperCase())
                || (row.name() != null && row.name().toLowerCase().contains(q.trim().toLowerCase())))
            .filter(row -> state == null || state.isBlank() || row.state().equalsIgnoreCase(state.trim()))
            .toList();
        int from = Math.min(all.size(), p * s);
        int to = Math.min(all.size(), from + s);
        return new CartDtos.PageResponse<>(all.subList(from, to), p, s, all.size(), (int) Math.ceil(all.size() / (double) s));
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
        return new CartDtos.CouponResponse(coupon.getId(), shop.getId(), coupon.getCode(), promo.getName(),
            CartService.toApiPromoType(promo.getPromoType()), CartMoney.of(promo.getValue()),
            promo.getMaxDiscountAmount() == null ? null : CartMoney.of(promo.getMaxDiscountAmount()),
            promo.getMinOrderAmount() == null ? null : CartMoney.of(promo.getMinOrderAmount()),
            promo.getStartsAt(), promo.getEndsAt(), promo.getUsageLimitTotal(), promo.getUsageLimitPerBuyer(),
            used, coupon.isActive() && promo.isActive(), state, coupon.getCreatedDateTime(), coupon.getModifiedDateTime());
    }

    private Coupon owned(Shop shop, UUID couponId) {
        return coupons.findByIdAndShopId(couponId, shop.getUuid())
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "ไม่พบคูปอง"));
    }

    private Shop shop(Long shopNo) {
        return shops.findByShopId(shopNo).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้านี้"));
    }
}
