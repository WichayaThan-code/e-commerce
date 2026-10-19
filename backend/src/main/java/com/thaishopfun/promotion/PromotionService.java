package com.thaishopfun.promotion;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.authz.AuthorizationService;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopRepository;
import com.thaishopfun.merchant.ShopStatus;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Shop promotions + their coupon code (one code per promotion). Merchant methods re-check shop membership
 * (another shop's promotion is indistinguishable from a missing one: 404). Coupon redemption at checkout is out of
 * scope: promotion_usages is only read (counters, delete guard).
 */
@Service
public class PromotionService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");
    static final int MAX_PAGE_SIZE = 100;
    static final int PUBLIC_LIMIT = 20;
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_-]{3,32}$");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000");
    private static final BigDecimal HUNDRED_MINUS_ONE = new BigDecimal("99");
    private static final int MAX_USAGE = 1_000_000;
    private static final int MAX_PER_BUYER = 1_000;
    private static final Map<String, String> MESSAGES = Map.ofEntries(
        Map.entry("NAME_REQUIRED", "กรุณาระบุชื่อโปรโมชัน"),
        Map.entry("NAME_TOO_LONG", "ชื่อโปรโมชันยาวเกิน 150 ตัวอักษร"),
        Map.entry("CODE_REQUIRED", "กรุณาระบุรหัสคูปอง"),
        Map.entry("CODE_INVALID", "รหัสคูปองใช้ได้เฉพาะ A-Z 0-9 _ - ยาว 3-32 ตัว"),
        Map.entry("PROMOTION_CODE_TAKEN", "รหัสคูปองนี้มีอยู่แล้วในร้าน"),
        Map.entry("TYPE_REQUIRED", "กรุณาเลือกประเภทโปรโมชัน"),
        Map.entry("TYPE_INVALID", "ประเภทโปรโมชันไม่ถูกต้อง (PERCENT, FIXED_AMOUNT, FREE_SHIPPING)"),
        Map.entry("VALUE_REQUIRED", "กรุณาระบุมูลค่าส่วนลด"),
        Map.entry("PERCENT_OUT_OF_RANGE", "ส่วนลดเปอร์เซ็นต์ต้องอยู่ระหว่าง 1-99"),
        Map.entry("AMOUNT_INVALID", "ส่วนลดต้องมากกว่า 0 และไม่เกิน 1,000,000 บาท"),
        Map.entry("AMOUNT_SCALE_INVALID", "จำนวนเงินมีทศนิยมได้ไม่เกิน 2 ตำแหน่ง"),
        Map.entry("MAX_DISCOUNT_INVALID", "ส่วนลดสูงสุดต้องมากกว่า 0 และไม่เกิน 1,000,000 บาท"),
        Map.entry("MAX_DISCOUNT_NOT_APPLICABLE", "ส่วนลดแบบจำนวนเงินไม่ต้องกำหนดส่วนลดสูงสุด"),
        Map.entry("MIN_ORDER_INVALID", "ยอดสั่งซื้อขั้นต่ำต้องไม่ติดลบและไม่เกิน 1,000,000 บาท"),
        Map.entry("DISCOUNT_EXCEEDS_MIN_ORDER", "ส่วนลดต้องไม่มากกว่ายอดสั่งซื้อขั้นต่ำ"),
        Map.entry("STARTS_AT_REQUIRED", "กรุณาระบุวันเริ่ม"),
        Map.entry("DATE_INVALID", "รูปแบบวันที่ไม่ถูกต้อง"),
        Map.entry("ENDS_BEFORE_START", "วันสิ้นสุดต้องหลังวันเริ่ม"),
        Map.entry("USAGE_LIMIT_INVALID", "จำนวนสิทธิ์ทั้งหมดต้องอยู่ระหว่าง 1-1,000,000"),
        Map.entry("USAGE_LIMIT_BELOW_USED", "จำนวนสิทธิ์ทั้งหมดต้องไม่น้อยกว่าจำนวนที่ถูกใช้ไปแล้ว"),
        Map.entry("PER_BUYER_LIMIT_INVALID", "สิทธิ์ต่อผู้ซื้อต้องอยู่ระหว่าง 1-1,000"),
        Map.entry("PER_BUYER_EXCEEDS_TOTAL", "สิทธิ์ต่อผู้ซื้อต้องไม่มากกว่าจำนวนสิทธิ์ทั้งหมด"),
        Map.entry("STATUS_INVALID", "สถานะไม่ถูกต้อง (UPCOMING, ACTIVE, PAUSED, EXPIRED)"));

    private final ShopRepository shops;
    private final PromotionRepository promotions;
    private final CouponRepository coupons;
    private final PromotionUsageRepository usages;
    private final AuthorizationService authorization;
    private final EntityManager entities;
    private final Clock clock;

    public PromotionService(ShopRepository shops, PromotionRepository promotions, CouponRepository coupons,
                            PromotionUsageRepository usages, AuthorizationService authorization, EntityManager entities) {
        this.shops = shops;
        this.promotions = promotions;
        this.coupons = coupons;
        this.usages = usages;
        this.authorization = authorization;
        this.entities = entities;
        this.clock = Clock.systemUTC();
    }

    // ---------------------------------------------------------------- merchant reads

    @Transactional(readOnly = true)
    public PromotionDtos.PromotionListResponse list(UUID userId, Long shopNo, String q, String status, String type, Integer page, Integer size) {
        var shop = requireShop(userId, shopNo);
        var violations = new ArrayList<FieldViolation>();
        var statusFilter = parseStatus(status, violations);
        var typeFilter = parseTypes(type, violations);
        if (!violations.isEmpty()) throw new ValidationException(violations);
        int pageNo = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size <= 0 ? 10 : Math.min(size, MAX_PAGE_SIZE);
        var now = Instant.now(clock);

        var where = new StringBuilder(" where p.shopId = :shopId");
        var params = new HashMap<String, Object>();
        params.put("shopId", shop.getUuid());
        if (statusFilter != null) {
            params.put("now", now);
            where.append(switch (statusFilter) {
                case EXPIRED -> " and p.endsAt is not null and p.endsAt <= :now";
                case PAUSED -> " and p.active = false and (p.endsAt is null or p.endsAt > :now)";
                case UPCOMING -> " and p.active = true and p.startsAt > :now and (p.endsAt is null or p.endsAt > :now)";
                case ACTIVE -> " and p.active = true and p.startsAt <= :now and (p.endsAt is null or p.endsAt > :now)";
            });
        }
        if (!typeFilter.isEmpty()) {
            where.append(" and p.type in :types");
            params.put("types", typeFilter);
        }
        if (q != null && !q.isBlank()) {
            where.append(" and (lower(p.name) like :q escape '!' or exists (select c from MerchantCoupon c where c.promotionId = p.id and lower(c.code) like :q escape '!'))");
            params.put("q", "%" + likeEscape(q.trim().toLowerCase(Locale.ROOT)) + "%");
        }
        var count = entities.createQuery("select count(p) from MerchantPromotion p" + where, Long.class);
        var query = entities.createQuery("select p from MerchantPromotion p" + where + " order by p.createdDateTime desc, p.id", Promotion.class);
        params.forEach((key, value) -> { count.setParameter(key, value); query.setParameter(key, value); });
        var total = count.getSingleResult();
        var rows = query.setFirstResult(pageNo * pageSize).setMaxResults(pageSize).getResultList();
        var items = responses(rows, now);
        int totalPages = (int) ((total + pageSize - 1) / pageSize);
        return new PromotionDtos.PromotionListResponse(items, pageNo, pageSize, total, totalPages, summary(shop, now));
    }

    @Transactional(readOnly = true)
    public PromotionDtos.PromotionResponse get(UUID userId, Long shopNo, UUID promotionId) {
        var shop = requireShop(userId, shopNo);
        return response(requirePromotion(shop, promotionId), Instant.now(clock));
    }

    // ---------------------------------------------------------------- merchant writes

    @Transactional
    public PromotionDtos.PromotionResponse create(UUID userId, Long shopNo, PromotionDtos.PromotionWriteRequest request) {
        var shop = requireShop(userId, shopNo);
        var promotion = new Promotion(shop.getUuid());
        var code = apply(promotion, shop, request, 0L, true);
        promotion.setActive(request == null || request.active() == null || request.active());
        promotion.stamp(userId);
        promotions.saveAndFlush(promotion);
        var coupon = new Coupon(shop.getUuid(), promotion.getId(), code);
        coupon.setActive(promotion.isActive());
        coupon.stamp(userId);
        coupons.saveAndFlush(coupon);
        return response(promotion, Instant.now(clock));
    }

    @Transactional
    public PromotionDtos.PromotionResponse update(UUID userId, Long shopNo, UUID promotionId, PromotionDtos.PromotionWriteRequest request) {
        var shop = requireShop(userId, shopNo);
        var promotion = requirePromotion(shop, promotionId);
        var used = usedCount(promotion.getId());
        var code = apply(promotion, shop, request, used, false);
        if (request != null && request.active() != null) promotion.setActive(request.active());
        promotion.stamp(userId);
        var rows = coupons.findByPromotionIdOrderByCreatedDateTimeAsc(promotion.getId());
        var coupon = rows.isEmpty() ? new Coupon(shop.getUuid(), promotion.getId(), code) : rows.get(0);
        coupon.setCode(code);
        coupon.setActive(promotion.isActive());
        coupon.stamp(userId);
        coupons.saveAndFlush(coupon);
        promotions.saveAndFlush(promotion);
        return response(promotion, Instant.now(clock));
    }

    @Transactional
    public PromotionDtos.PromotionResponse setActive(UUID userId, Long shopNo, UUID promotionId, boolean active) {
        var shop = requireShop(userId, shopNo);
        var promotion = requirePromotion(shop, promotionId);
        promotion.setActive(active);
        promotion.stamp(userId);
        for (var coupon : coupons.findByPromotionIdOrderByCreatedDateTimeAsc(promotion.getId())) {
            coupon.setActive(active);
            coupon.stamp(userId);
        }
        promotions.saveAndFlush(promotion);
        return response(promotion, Instant.now(clock));
    }

    /** Hard delete (coupons first). A promotion already used in an order cannot be deleted: pause it instead. */
    @Transactional
    public void delete(UUID userId, Long shopNo, UUID promotionId) {
        var shop = requireShop(userId, shopNo);
        var promotion = requirePromotion(shop, promotionId);
        if (usages.existsByPromotionId(promotion.getId())) {
            throw new AuthException(HttpStatus.CONFLICT, "PROMOTION_IN_USE", "โปรโมชันนี้ถูกใช้ในคำสั่งซื้อแล้ว ลบไม่ได้ — ใช้ \"หยุดชั่วคราว\" แทน");
        }
        coupons.deleteByPromotionId(promotion.getId());
        promotions.delete(promotion);
        promotions.flush();
    }

    // ---------------------------------------------------------------- storefront

    /** Active, started, not ended, shown on the storefront, coupon active and not used up. APPROVED shops only. */
    @Transactional(readOnly = true)
    public List<PromotionDtos.PublicPromotionResponse> publicList(String slug) {
        var shop = shops.findBySlug(slug).filter(item -> item.getStatus() == ShopStatus.APPROVED)
            .orElseThrow(PromotionService::shopNotFound);
        var now = Instant.now(clock);
        var rows = entities.createQuery(
                "select p from MerchantPromotion p where p.shopId = :shopId and p.active = true and p.publicVisible = true"
                    + " and p.startsAt <= :now and (p.endsAt is null or p.endsAt > :now)"
                    + " order by case when p.endsAt is null then 1 else 0 end, p.endsAt asc, p.createdDateTime desc",
                Promotion.class)
            .setParameter("shopId", shop.getUuid())
            .setParameter("now", now)
            .getResultList();
        if (rows.isEmpty()) return List.of();
        var ids = rows.stream().map(Promotion::getId).toList();
        var codes = firstActiveCodes(ids);
        var used = totals(ids);
        var result = new ArrayList<PromotionDtos.PublicPromotionResponse>();
        for (var promotion : rows) {
            var code = codes.get(promotion.getId());
            if (code == null) continue;
            var limit = promotion.getUsageLimitTotal();
            if (limit != null && used.getOrDefault(promotion.getId(), Totals.EMPTY).count() >= limit) continue;
            result.add(new PromotionDtos.PublicPromotionResponse(code, promotion.getName(), promotion.getType(), promotion.getValue(),
                promotion.getMinOrderAmount(), promotion.getMaxDiscountAmount(), promotion.getStartsAt(), promotion.getEndsAt()));
            if (result.size() >= PUBLIC_LIMIT) break;
        }
        return result;
    }

    // ---------------------------------------------------------------- validation

    /** Validates and copies the request onto the entity; returns the normalised coupon code. */
    private String apply(Promotion promotion, Shop shop, PromotionDtos.PromotionWriteRequest request, long used, boolean creating) {
        var violations = new ArrayList<FieldViolation>();
        if (request == null) request = new PromotionDtos.PromotionWriteRequest(null, null, null, null, null, null, null, null, null, null, null, null);

        var name = trimToNull(request.name());
        if (name == null) violations.add(violation("name", "NAME_REQUIRED"));
        else if (name.length() > 150) violations.add(violation("name", "NAME_TOO_LONG"));

        var code = trimToNull(request.code());
        if (code != null) code = code.toUpperCase(Locale.ROOT);
        if (code == null) violations.add(violation("code", "CODE_REQUIRED"));
        else if (!CODE.matcher(code).matches()) violations.add(violation("code", "CODE_INVALID"));

        PromotionType type = null;
        var rawType = trimToNull(request.type());
        if (rawType == null) violations.add(violation("type", "TYPE_REQUIRED"));
        else {
            try { type = PromotionType.valueOf(rawType.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { violations.add(violation("type", "TYPE_INVALID")); }
        }

        var value = request.value();
        var maxDiscount = request.maxDiscountAmount();
        var minOrder = request.minOrderAmount();
        if (type == PromotionType.PERCENT) {
            if (value == null) violations.add(violation("value", "VALUE_REQUIRED"));
            else if (value.compareTo(BigDecimal.ONE) < 0 || value.compareTo(HUNDRED_MINUS_ONE) > 0) violations.add(violation("value", "PERCENT_OUT_OF_RANGE"));
            else if (value.stripTrailingZeros().scale() > 2) violations.add(violation("value", "AMOUNT_SCALE_INVALID"));
        } else if (type == PromotionType.FIXED_AMOUNT) {
            if (value == null) violations.add(violation("value", "VALUE_REQUIRED"));
            else checkAmount(value, "value", "AMOUNT_INVALID", false, violations);
            if (maxDiscount != null) violations.add(violation("maxDiscountAmount", "MAX_DISCOUNT_NOT_APPLICABLE"));
        } else if (type == PromotionType.FREE_SHIPPING) {
            value = BigDecimal.ZERO;
        }
        if (maxDiscount != null && type != PromotionType.FIXED_AMOUNT) checkAmount(maxDiscount, "maxDiscountAmount", "MAX_DISCOUNT_INVALID", false, violations);
        if (minOrder != null) checkAmount(minOrder, "minOrderAmount", "MIN_ORDER_INVALID", true, violations);
        if (type == PromotionType.FIXED_AMOUNT && value != null && minOrder != null && minOrder.signum() > 0 && value.compareTo(minOrder) > 0) {
            violations.add(violation("value", "DISCOUNT_EXCEEDS_MIN_ORDER"));
        }

        var startsAt = parseInstant(request.startsAt(), "startsAt", true, violations);
        var endsAt = parseInstant(request.endsAt(), "endsAt", false, violations);
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) violations.add(violation("endsAt", "ENDS_BEFORE_START"));

        var limit = request.usageLimitTotal();
        if (limit != null) {
            if (limit < 1 || limit > MAX_USAGE) violations.add(violation("usageLimitTotal", "USAGE_LIMIT_INVALID"));
            else if (limit < used) violations.add(violation("usageLimitTotal", "USAGE_LIMIT_BELOW_USED"));
        }
        var perBuyer = request.usageLimitPerBuyer();
        if (perBuyer != null) {
            if (perBuyer < 1 || perBuyer > MAX_PER_BUYER) violations.add(violation("usageLimitPerBuyer", "PER_BUYER_LIMIT_INVALID"));
            else if (limit != null && limit >= 1 && perBuyer > limit) violations.add(violation("usageLimitPerBuyer", "PER_BUYER_EXCEEDS_TOTAL"));
        }

        if (!violations.isEmpty()) throw new ValidationException(ValidationException.topCode(violations), violations);
        if (coupons.codeTaken(shop.getUuid(), code, creating ? UUID.randomUUID() : promotion.getId())) {
            throw new ValidationException(HttpStatus.CONFLICT, "PROMOTION_CODE_TAKEN", List.of(violation("code", "PROMOTION_CODE_TAKEN")));
        }

        promotion.setName(name);
        promotion.setType(type);
        promotion.setValue(value.setScale(2, java.math.RoundingMode.UNNECESSARY));
        promotion.setMinOrderAmount(minOrder == null ? null : minOrder.setScale(2, java.math.RoundingMode.UNNECESSARY));
        promotion.setMaxDiscountAmount(maxDiscount == null ? null : maxDiscount.setScale(2, java.math.RoundingMode.UNNECESSARY));
        promotion.setStartsAt(startsAt);
        promotion.setEndsAt(endsAt);
        promotion.setUsageLimitTotal(limit);
        promotion.setUsageLimitPerBuyer(perBuyer);
        if (request.showOnStorefront() != null) promotion.setPublicVisible(request.showOnStorefront());
        return code;
    }

    private static void checkAmount(BigDecimal amount, String field, String code, boolean allowZero, List<FieldViolation> violations) {
        if (allowZero ? amount.signum() < 0 : amount.signum() <= 0) violations.add(violation(field, code));
        else if (amount.compareTo(MAX_AMOUNT) > 0) violations.add(violation(field, code));
        else if (amount.stripTrailingZeros().scale() > 2) violations.add(violation(field, "AMOUNT_SCALE_INVALID"));
    }

    /** ISO instant with offset, or a local date-time read in Asia/Bangkok. */
    static Instant parseInstant(String raw, String field, boolean required, List<FieldViolation> violations) {
        var text = trimToNull(raw);
        if (text == null) {
            if (required) violations.add(violation(field, "STARTS_AT_REQUIRED"));
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(text).atZone(ZONE).toInstant();
            } catch (DateTimeParseException e) {
                violations.add(violation(field, "DATE_INVALID"));
                return null;
            }
        }
    }

    private static PromotionStatus parseStatus(String status, List<FieldViolation> violations) {
        var text = trimToNull(status);
        if (text == null) return null;
        try { return PromotionStatus.valueOf(text.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { violations.add(violation("status", "STATUS_INVALID")); return null; }
    }

    private static Set<PromotionType> parseTypes(String type, List<FieldViolation> violations) {
        var text = trimToNull(type);
        if (text == null) return Set.of();
        var result = EnumSet.noneOf(PromotionType.class);
        for (var part : text.split(",")) {
            var item = part.trim();
            if (item.isEmpty()) continue;
            try { result.add(PromotionType.valueOf(item.toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException e) { violations.add(violation("type", "TYPE_INVALID")); return Set.of(); }
        }
        return result;
    }

    // ---------------------------------------------------------------- mapping

    private record Totals(long count, BigDecimal discount) {
        static final Totals EMPTY = new Totals(0, BigDecimal.ZERO);
    }

    private PromotionDtos.PromotionResponse response(Promotion promotion, Instant now) {
        return responses(List.of(promotion), now).get(0);
    }

    private List<PromotionDtos.PromotionResponse> responses(List<Promotion> rows, Instant now) {
        if (rows.isEmpty()) return List.of();
        var ids = rows.stream().map(Promotion::getId).toList();
        var codes = new HashMap<UUID, String>();
        for (var coupon : coupons.findByPromotionIdInOrderByCreatedDateTimeAsc(ids)) codes.putIfAbsent(coupon.getPromotionId(), coupon.getCode());
        var totals = totals(ids);
        return rows.stream().map(promotion -> {
            var total = totals.getOrDefault(promotion.getId(), Totals.EMPTY);
            return new PromotionDtos.PromotionResponse(promotion.getId(), codes.get(promotion.getId()), promotion.getName(), promotion.getType(),
                promotion.getValue(), promotion.getMinOrderAmount(), promotion.getMaxDiscountAmount(), promotion.getStartsAt(), promotion.getEndsAt(),
                promotion.getUsageLimitTotal(), promotion.getUsageLimitPerBuyer(), promotion.isPublicVisible(), promotion.isActive(),
                promotion.status(now), total.count(), total.discount(), promotion.getCreatedDateTime(), promotion.getModifiedDateTime());
        }).toList();
    }

    private Map<UUID, String> firstActiveCodes(List<UUID> ids) {
        var codes = new HashMap<UUID, String>();
        for (var coupon : coupons.findByPromotionIdInOrderByCreatedDateTimeAsc(ids)) {
            if (coupon.isActive()) codes.putIfAbsent(coupon.getPromotionId(), coupon.getCode());
        }
        return codes;
    }

    private Map<UUID, Totals> totals(Collection<UUID> ids) {
        var result = new HashMap<UUID, Totals>();
        if (ids.isEmpty()) return result;
        for (var row : usages.totalsByPromotion(ids)) {
            result.put((UUID) row[0], new Totals(((Number) row[1]).longValue(), toDecimal(row[2])));
        }
        return result;
    }

    private long usedCount(UUID promotionId) {
        return totals(List.of(promotionId)).getOrDefault(promotionId, Totals.EMPTY).count();
    }

    private PromotionDtos.PromotionSummary summary(Shop shop, Instant now) {
        var all = promotions.findByShopId(shop.getUuid());
        long active = 0, upcoming = 0, paused = 0, expired = 0, percent = 0, fixed = 0, shipping = 0;
        for (var promotion : all) {
            switch (promotion.status(now)) {
                case ACTIVE -> active++;
                case UPCOMING -> upcoming++;
                case PAUSED -> paused++;
                case EXPIRED -> expired++;
            }
            switch (promotion.getType()) {
                case PERCENT -> percent++;
                case FIXED_AMOUNT -> fixed++;
                case FREE_SHIPPING -> shipping++;
            }
        }
        long usageCount = 0;
        var discount = BigDecimal.ZERO;
        for (var total : totals(all.stream().map(Promotion::getId).toList()).values()) {
            usageCount += total.count();
            discount = discount.add(total.discount());
        }
        return new PromotionDtos.PromotionSummary(all.size(), active, upcoming, paused, expired, percent, fixed, shipping, usageCount, discount);
    }

    private static BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return new BigDecimal(number.toString());
        return BigDecimal.ZERO;
    }

    // ---------------------------------------------------------------- helpers

    private Shop requireShop(UUID userId, Long shopNo) {
        if (userId == null || shopNo == null) throw shopNotFound();
        var shop = shops.findByShopId(shopNo).orElseThrow(PromotionService::shopNotFound);
        var grants = authorization.grants(userId);
        if (!grants.superAdmin() && !grants.shopRoles().containsKey(shop.getId())) throw shopNotFound();
        return shop;
    }

    private Promotion requirePromotion(Shop shop, UUID promotionId) {
        if (promotionId == null) throw promotionNotFound();
        return promotions.findByIdAndShopId(promotionId, shop.getUuid()).orElseThrow(PromotionService::promotionNotFound);
    }

    private static String likeEscape(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static FieldViolation violation(String field, String code) {
        return new FieldViolation(field, code, MESSAGES.getOrDefault(code, "ข้อมูลไม่ถูกต้อง"));
    }

    private static AuthException shopNotFound() { return new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้า"); }
    private static AuthException promotionNotFound() { return new AuthException(HttpStatus.NOT_FOUND, "PROMOTION_NOT_FOUND", "ไม่พบโปรโมชัน"); }
}