package com.thaishopfun.merchant.plan;

import com.thaishopfun.admin.AdminShopUsageService;
import com.thaishopfun.admin.AdminPlatformDtos.PlanLimits;
import com.thaishopfun.admin.AdminPlatformDtos.ShopUsage;
import com.thaishopfun.auth.authz.AuthorizationService;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.plan.MerchantPlanUsageDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Merchant plan usage snapshot. Counting reuses {@link AdminShopUsageService} (same SQL formulas);
 * this layer only maps permissions, plan resolution, and display statuses for the shop member.
 */
@Service
public class MerchantPlanUsageService {
    public static final double WARN_AT_RATIO = 0.80d;
    public static final String TIMEZONE = "Asia/Bangkok";
    public static final int ORDERS_ROLLING_DAYS = 30;

    private static final String DISPLAY_POLICY_NOTE =
        "เกณฑ์เตือนบนหน้าจอ (ใกล้เต็มตั้งแต่ 80%) — ไม่ใช่จุดที่ระบบเริ่มบล็อก และไม่ใช่กติกาแพ็กเกจเดิม";

    private final AdminShopUsageService usage;
    private final AuthorizationService authz;
    private final JdbcTemplate jdbc;

    public MerchantPlanUsageService(AdminShopUsageService usage, AuthorizationService authz, JdbcTemplate jdbc) {
        this.usage = usage;
        this.authz = authz;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PlanUsageResponse snapshot(Shop shop, UUID viewerId) {
        var asOf = Instant.now();
        var periodEnd = asOf;
        var periodStart = asOf.minus(Duration.ofDays(ORDERS_ROLLING_DAYS));

        var effectiveCode = effectivePlanCode(shop.getPlanCode());
        var key = new AdminShopUsageService.ShopKey(
            shop.getUuid(),
            shop.getId(),
            shop.getName(),
            apiShopStatus(shop.getStatus() == null ? null : shop.getStatus().name()),
            shop.getSlug(),
            effectiveCode
        );
        var raw = usage.usage(List.of(key)).get(shop.getUuid());
        var planRow = resolvePlan(effectiveCode);
        var plan = toPlanInfo(shop.getPlanCode(), planRow);
        var limits = planRow == null ? null : planRow.limits();

        boolean canProducts = granted(viewerId, shop.getId(), "PRODUCT_READ");
        boolean canOrders = granted(viewerId, shop.getId(), "ORDER_READ");
        boolean canStaff = granted(viewerId, shop.getId(), "USER_READ");
        boolean canStorage = granted(viewerId, shop.getId(), "SHOP_READ");

        var metrics = new ArrayList<MetricUsage>();
        metrics.add(productsMetric(raw, limits, plan, canProducts, asOf));
        metrics.add(ordersMetric(raw, limits, plan, canOrders, periodStart, periodEnd));
        metrics.add(staffMetric(raw, limits, plan, canStaff, asOf));
        metrics.add(storageMetric(raw, limits, plan, canStorage, asOf));

        return new PlanUsageResponse(
            shop.getId(),
            shop.getName(),
            key.status(),
            plan,
            shop.getPlanBillingCycle(),
            shop.getPlanExpiresAt(),
            asOf,
            TIMEZONE,
            new DisplayPolicy(WARN_AT_RATIO, DISPLAY_POLICY_NOTE),
            List.copyOf(metrics),
            enforcementNotes(),
            loadCatalog()
        );
    }

    private List<EnforcementNote> enforcementNotes() {
        return List.of(
            new EnforcementNote(
                "STAFF",
                "ENFORCED",
                "โควตาสมาชิกบังคับตอนเชิญสมาชิกใหม่ (นับ ACTIVE + คำเชิญ PENDING ที่ยังไม่หมดอายุ)"
            ),
            new EnforcementNote(
                "PRODUCTS",
                "ENFORCED",
                "โควตาสินค้าบังคับตอนเผยแพร่เท่านั้น (นับเฉพาะ PUBLISHED) — สร้างร่าง/เก็บถาวรได้ไม่จำกัด"
            ),
            new EnforcementNote(
                "ORDERS_ROLLING_30D",
                "ENFORCED",
                "โควตาออเดอร์บังคับตอน checkout — นับย้อนหลัง " + ORDERS_ROLLING_DAYS + " วันแบบเลื่อน"
            ),
            new EnforcementNote(
                "STORAGE",
                "ENFORCED",
                "โควตาพื้นที่บังคับตอนอัปโหลดไฟล์ — นับ media_files + merchant_documents"
            )
        );
    }

    private List<CatalogPlan> loadCatalog() {
        return jdbc.query("""
            SELECT code, name, description, price_monthly, price_yearly, sort_order,
                   limit_products, limit_orders_per_month, limit_staff, limit_storage_bytes
              FROM plans WHERE active = TRUE ORDER BY sort_order, code
            """, (rs, i) -> new CatalogPlan(
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getBigDecimal("price_monthly"),
            rs.getBigDecimal("price_yearly"),
            new PlanLimits(
                longOrNull(rs, "limit_products"),
                longOrNull(rs, "limit_orders_per_month"),
                longOrNull(rs, "limit_staff"),
                longOrNull(rs, "limit_storage_bytes")
            ),
            rs.getInt("sort_order"),
            "BASIC".equals(rs.getString("code"))
        ));
    }

    private static String effectivePlanCode(String planCode) {
        return (planCode == null || planCode.isBlank()) ? PlanQuotaService.DEFAULT_PLAN : planCode.trim();
    }

    private MetricUsage productsMetric(ShopUsage raw, PlanLimits limits, PlanInfo plan, boolean allowed, Instant asOf) {
        var def = new MetricDefinition(
            "products status = PUBLISHED AND deleted_at IS NULL",
            "สินค้าที่เผยแพร่อยู่เท่านั้น (ตรงกับโควตา limit_products)",
            "DRAFT / ARCHIVED และสินค้าที่ soft-delete แล้ว — ไม่นับในเพดาน; ไม่นับ SKU เป็นโควตานี้",
            "เพดานอ้างอิง plans.limit_products; breakdown.draft / archived / activeSkus / images เป็นข้อมูลประกอบ ไม่ใช่เพดานแยก"
        );
        if (!allowed) return restricted("PRODUCTS", "PRODUCT_READ", "COUNT", def, nonePeriod(asOf));
        long used = raw == null || raw.products() == null ? 0L : raw.products().published();
        var breakdown = new LinkedHashMap<String, Long>();
        if (raw != null && raw.products() != null) {
            breakdown.put("published", raw.products().published());
            breakdown.put("draft", raw.products().draft());
            breakdown.put("archived", raw.products().archived());
            breakdown.put("totalNonDeleted", raw.products().total());
            breakdown.put("activeSkus", raw.products().activeSkus());
            breakdown.put("images", raw.products().images());
        }
        return metric(
            "PRODUCTS", "PRODUCT_READ", used, limitOf(plan, limits == null ? null : limits.products()),
            "COUNT", "COMPLETE", nonePeriod(asOf), def, breakdown
        );
    }

    private MetricUsage ordersMetric(
        ShopUsage raw, PlanLimits limits, PlanInfo plan, boolean allowed, Instant start, Instant end
    ) {
        var def = new MetricDefinition(
            "orders.placed_at within rolling window",
            "ออเดอร์ทุกสถานะที่ placed_at อยู่ในช่วงย้อนหลัง " + ORDERS_ROLLING_DAYS + " วัน (เลื่อนตามเวลาอ่าน)",
            "ออเดอร์นอกช่วง; ไม่ใช่เดือนปฏิทิน (calendar month)",
            "เพดานอ้างอิง plans.limit_orders_per_month แต่การนับจริงเป็น rolling " + ORDERS_ROLLING_DAYS + " วัน — ห้ามเรียกว่าเดือนนี้"
        );
        var period = new PeriodInfo("ROLLING_DAYS", ORDERS_ROLLING_DAYS, start, end);
        if (!allowed) return restricted("ORDERS_ROLLING_30D", "ORDER_READ", "COUNT", def, period);
        long used = raw == null || raw.orders() == null ? 0L : raw.orders().last30Days();
        var breakdown = new LinkedHashMap<String, Long>();
        if (raw != null && raw.orders() != null) {
            breakdown.put("totalAllTime", raw.orders().total());
            breakdown.put("open", raw.orders().open());
            breakdown.put("completed", raw.orders().completed());
            breakdown.put("cancelled", raw.orders().cancelled());
        }
        return metric(
            "ORDERS_ROLLING_30D", "ORDER_READ", used, limitOf(plan, limits == null ? null : limits.ordersPerMonth()),
            "COUNT", "COMPLETE", period, def, breakdown
        );
    }

    private MetricUsage staffMetric(ShopUsage raw, PlanLimits limits, PlanInfo plan, boolean allowed, Instant asOf) {
        var def = new MetricDefinition(
            "shop_members ACTIVE",
            "สมาชิกสถานะ ACTIVE ที่ยังไม่ถูกถอด (รวมเจ้าของ)",
            "คำเชิญ PENDING และสมาชิกที่ถอดแล้ว — การบังคับโควตาตอนเชิญนับ ACTIVE+PENDING แต่หน้านี้แสดงเฉพาะ ACTIVE ให้ตรง Admin",
            "เพดานอ้างอิง plans.limit_staff"
        );
        if (!allowed) return restricted("STAFF", "USER_READ", "COUNT", def, nonePeriod(asOf));
        long used = raw == null || raw.staff() == null ? 0L : raw.staff().active();
        var breakdown = new LinkedHashMap<String, Long>();
        if (raw != null && raw.staff() != null && raw.staff().byRole() != null) {
            for (var e : raw.staff().byRole().entrySet()) breakdown.put(e.getKey(), e.getValue());
        }
        return metric(
            "STAFF", "USER_READ", used, limitOf(plan, limits == null ? null : limits.staff()),
            "COUNT", "COMPLETE", nonePeriod(asOf), def, breakdown
        );
    }

    private MetricUsage storageMetric(ShopUsage raw, PlanLimits limits, PlanInfo plan, boolean allowed, Instant asOf) {
        var note = raw != null && raw.storage() != null && raw.storage().note() != null
            ? raw.storage().note()
            : "trackedBytes = media_files + merchant_documents; product images / logo / cover on disk ไม่มีคอลัมน์ขนาด";
        var def = new MetricDefinition(
            "media_files + merchant_documents",
            "ไฟล์ใน media_files (status <> DELETED) และเอกสารสมัครร้านใน merchant_documents ที่ผูก application ของร้าน",
            "รูปสินค้า/โลโก้/ปกบนดิสก์ที่ไม่มีขนาดในฐานข้อมูล — นับได้แค่จำนวนรูปสินค้า (images) ไม่รวมเข้า trackedBytes",
            note
        );
        if (!allowed) return restricted("STORAGE", "SHOP_READ", "BYTES", def, nonePeriod(asOf));
        long used = raw == null || raw.storage() == null ? 0L : raw.storage().trackedBytes();
        var breakdown = new LinkedHashMap<String, Long>();
        if (raw != null && raw.storage() != null) {
            breakdown.put("mediaFiles", raw.storage().mediaFiles());
            breakdown.put("mediaBytes", raw.storage().mediaBytes());
            breakdown.put("documents", raw.storage().documents());
            breakdown.put("documentBytes", raw.storage().documentBytes());
            breakdown.put("productImagesCountOnly", raw.storage().productImages());
        }
        return metric(
            "STORAGE", "SHOP_READ", used, limitOf(plan, limits == null ? null : limits.storageBytes()),
            "BYTES", "PARTIAL", nonePeriod(asOf), def, breakdown
        );
    }

    private static PeriodInfo nonePeriod(Instant asOf) {
        return new PeriodInfo("NONE", null, null, asOf);
    }

    private static MetricUsage restricted(String code, String permission, String unit, MetricDefinition def, PeriodInfo period) {
        return new MetricUsage(
            code, "RESTRICTED", permission, false, null,
            new LimitInfo("UNKNOWN", null), unit, "UNKNOWN", period, def,
            "RESTRICTED", null, null, Map.of()
        );
    }

    private MetricUsage metric(
        String code,
        String permission,
        long used,
        LimitInfo limit,
        String unit,
        String completeness,
        PeriodInfo period,
        MetricDefinition def,
        Map<String, Long> breakdown
    ) {
        var evaluated = evaluate(used, limit);
        return new MetricUsage(
            code, "AVAILABLE", permission, true, used, limit, unit, completeness, period, def,
            evaluated.status(), evaluated.remaining(), evaluated.ratio(), Map.copyOf(breakdown)
        );
    }

    /** Package-visible for unit tests. */
    static EvaluatedQuota evaluate(long used, LimitInfo limit) {
        if (limit == null || "UNKNOWN".equals(limit.kind()) || "NOT_APPLICABLE".equals(limit.kind())) {
            return new EvaluatedQuota(limit == null || "UNKNOWN".equals(limit.kind()) ? "UNKNOWN" : "NOT_APPLICABLE", null, null);
        }
        if ("UNLIMITED".equals(limit.kind())) {
            return new EvaluatedQuota("UNLIMITED", null, null);
        }
        if ("ZERO".equals(limit.kind()) || (limit.value() != null && limit.value() == 0L)) {
            return new EvaluatedQuota(used > 0 ? "OVER" : "ZERO_QUOTA", 0L, null);
        }
        long cap = limit.value() == null ? 0L : limit.value();
        if (cap <= 0L) {
            return new EvaluatedQuota(used > 0 ? "OVER" : "ZERO_QUOTA", 0L, null);
        }
        double ratio = ((double) used) / ((double) cap);
        long remaining = Math.max(cap - used, 0L);
        if (used > cap) return new EvaluatedQuota("OVER", remaining, ratio);
        if (used == cap) return new EvaluatedQuota("FULL", remaining, ratio);
        if (ratio >= WARN_AT_RATIO) return new EvaluatedQuota("WARN", remaining, ratio);
        return new EvaluatedQuota("OK", remaining, ratio);
    }

    record EvaluatedQuota(String status, Long remaining, Double ratio) {}

    private LimitInfo limitOf(PlanInfo plan, Long rawLimit) {
        if ("NONE".equals(plan.resolution())) {
            return new LimitInfo("NOT_APPLICABLE", null);
        }
        if ("UNKNOWN".equals(plan.resolution())) {
            return new LimitInfo("UNKNOWN", null);
        }
        // ACTIVE or RETIRED with a known catalog row
        if (rawLimit == null) return new LimitInfo("UNLIMITED", null);
        if (rawLimit == 0L) return new LimitInfo("ZERO", 0L);
        if (rawLimit < 0L) return new LimitInfo("UNKNOWN", null);
        return new LimitInfo("FINITE", rawLimit);
    }

    private PlanInfo toPlanInfo(String planCode, PlanRow row) {
        if (planCode == null || planCode.isBlank()) {
            if (row != null && row.active()) {
                return new PlanInfo(
                    "ASSIGNED", row.code(), row.name(), "ACTIVE",
                    "ร้านยังไม่มี plan_code บันทึกไว้ — ระบบใช้แพ็กเกจ Free สำหรับโควตาและการแสดงผล"
                );
            }
            return new PlanInfo(
                "ASSIGNED", PlanQuotaService.DEFAULT_PLAN, row == null ? "Free" : row.name(), "ACTIVE",
                "ร้านยังไม่มี plan_code บันทึกไว้ — ระบบใช้แพ็กเกจ Free สำหรับโควตาและการแสดงผล"
            );
        }
        if (row == null) {
            return new PlanInfo(
                "ASSIGNED", planCode, null, "UNKNOWN",
                "พบรหัสแผนบนร้านแต่ไม่พบในตาราง plans — ไม่ตีความว่าไม่จำกัด"
            );
        }
        if (!row.active()) {
            return new PlanInfo(
                "ASSIGNED", row.code(), row.name(), "RETIRED",
                "แผนนี้ถูกปิดใช้งานในแคตตาล็อกแล้ว แต่ร้านยังอ้างอิงรหัสนี้อยู่ — แสดงเพดานจากแถวแผนถ้ามี"
            );
        }
        return new PlanInfo("ASSIGNED", row.code(), row.name(), "ACTIVE", null);
    }

    private PlanRow resolvePlan(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            planCode = PlanQuotaService.DEFAULT_PLAN;
        }
        var rows = jdbc.query("""
            SELECT code, name, active, limit_products, limit_orders_per_month, limit_staff, limit_storage_bytes
              FROM plans WHERE code = ?
            """, (rs, i) -> new PlanRow(
            rs.getString("code"),
            rs.getString("name"),
            rs.getBoolean("active"),
            new PlanLimits(
                longOrNull(rs, "limit_products"),
                longOrNull(rs, "limit_orders_per_month"),
                longOrNull(rs, "limit_staff"),
                longOrNull(rs, "limit_storage_bytes")
            )
        ), planCode);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean granted(UUID userId, Long shopNo, String permission) {
        return authz.checkShop(userId, shopNo, permission) == AuthorizationService.ShopDecision.GRANTED;
    }

    private static Long longOrNull(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Object v = rs.getObject(col);
        if (v == null) return null;
        return rs.getLong(col);
    }

    /** Same mapping as AdminRefs.apiShopStatus (kept local — AdminRefs is package-private). */
    static String apiShopStatus(String stored) {
        if (stored == null) return null;
        return switch (stored) {
            case "ACTIVE" -> "APPROVED";
            case "CLOSED" -> "REJECTED";
            default -> stored;
        };
    }

    private record PlanRow(String code, String name, boolean active, PlanLimits limits) {}
}
