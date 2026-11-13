package com.thaishopfun.merchant.plan;

import com.thaishopfun.admin.AdminPlatformDtos.PlanLimits;
import com.thaishopfun.admin.AdminShopService;
import com.thaishopfun.admin.ShopPlan;
import com.thaishopfun.admin.notification.AdminNotificationService;
import com.thaishopfun.auth.AuthException;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopRepository;
import com.thaishopfun.merchant.notification.MerchantLifecycleNotificationWriter;
import com.thaishopfun.merchant.plan.ShopPlanPurchaseDtos.*;
import com.thaishopfun.payment.PaymentFileService;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ShopPlanPurchaseService {
    private static final Logger log = LoggerFactory.getLogger(ShopPlanPurchaseService.class);
    public static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private static final List<String> OPEN = List.of(
        ShopPlanSubscription.PENDING_PAYMENT, ShopPlanSubscription.PENDING_REVIEW);

    private final ShopRepository shops;
    private final ShopPlanSubscriptionRepository subscriptions;
    private final JdbcTemplate jdbc;
    private final EntityManager entities;
    private final PlatformBillingProperties billing;
    private final PaymentFileService paymentFiles;
    private final AdminShopService adminShops;
    private final AdminNotificationService adminNotifications;
    private final MerchantLifecycleNotificationWriter merchantNotifications;

    public ShopPlanPurchaseService(
        ShopRepository shops,
        ShopPlanSubscriptionRepository subscriptions,
        JdbcTemplate jdbc,
        EntityManager entities,
        PlatformBillingProperties billing,
        PaymentFileService paymentFiles,
        AdminShopService adminShops,
        AdminNotificationService adminNotifications,
        MerchantLifecycleNotificationWriter merchantNotifications
    ) {
        this.shops = shops;
        this.subscriptions = subscriptions;
        this.jdbc = jdbc;
        this.entities = entities;
        this.billing = billing;
        this.paymentFiles = paymentFiles;
        this.adminShops = adminShops;
        this.adminNotifications = adminNotifications;
        this.merchantNotifications = merchantNotifications;
    }

    @Transactional(readOnly = true)
    public PurchaseCatalogResponse catalog() {
        var plans = loadActivePlans().stream()
            .filter(p -> !"FREE".equalsIgnoreCase(p.getCode()))
            .map(this::toCatalogPlan)
            .toList();
        return new PurchaseCatalogResponse(plans, transferAccount(), "THB", Instant.now());
    }

    @Transactional(readOnly = true)
    public PurchaseHistoryResponse history(Shop shop) {
        var items = subscriptions.findByShopIdOrderByCreatedAtDesc(shop.getUuid()).stream()
            .map(row -> toView(row, shop, false))
            .toList();
        var open = items.stream().filter(i -> OPEN.contains(i.status())).findFirst().orElse(null);
        return new PurchaseHistoryResponse(items, open);
    }

    @Transactional
    public PurchaseOrderView create(Shop shop, CreatePurchaseRequest request, UUID actorId) {
        var cycle = requireBillingCycle(request == null ? null : request.billingCycle());
        var planCode = requirePaidPlanCode(request == null ? null : request.planCode());
        var plan = requireActivePlan(planCode);
        var current = effectivePlanCode(shop.getPlanCode());
        var currentCycle = shop.getPlanBillingCycle() == null ? null : shop.getPlanBillingCycle().trim().toUpperCase(Locale.ROOT);
        if (planCode.equals(current) && cycle.equals(currentCycle)) {
            throw conflict("PLAN_ALREADY_ACTIVE",
                "ร้านใช้อยู่แล้วเป็นแพ็กเกจ " + plan.getName() + " รอบ" + cycleLabel(cycle)
                    + " — เลือกรอบบิลหรือแพ็กเกจอื่น หรือรอให้ใกล้หมดอายุแล้วต่ออายุ");
        }
        if (!subscriptions.findOpen(shop.getUuid(), OPEN).isEmpty()) {
            throw conflict("PLAN_PURCHASE_OPEN",
                "มีคำสั่งซื้อแพ็กเกจที่รอชำระเงินหรือรอตรวจสอบอยู่แล้ว — ยกเลิกหรือรอผลก่อนสั่งใหม่");
        }
        var amount = priceFor(plan, cycle);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw bad("PLAN_PRICE_INVALID", "แพ็กเกจนี้ยังไม่มีราคาสำหรับรอบบิลที่เลือก — ติดต่อแอดมิน");
        }
        var row = new ShopPlanSubscription(shop.getUuid(), planCode, cycle, amount, actorId);
        try {
            subscriptions.saveAndFlush(row);
        } catch (DataIntegrityViolationException ex) {
            throw conflict("PLAN_PURCHASE_OPEN",
                "มีคำสั่งซื้อแพ็กเกจที่รอชำระเงินหรือรอตรวจสอบอยู่แล้ว — ยกเลิกหรือรอผลก่อนสั่งใหม่");
        }
        return toView(row, shop, true);
    }

    @Transactional
    public PurchaseOrderView attachProof(Shop shop, UUID subscriptionId, MultipartFile file, UUID actorId) {
        var row = requireOwnedOpen(shop, subscriptionId);
        if (!ShopPlanSubscription.PENDING_PAYMENT.equals(row.getStatus())
            && !ShopPlanSubscription.PENDING_REVIEW.equals(row.getStatus())) {
            throw conflict("PLAN_PURCHASE_NOT_OPEN", "แนบสลิปได้เฉพาะคำสั่งซื้อที่รอชำระเงินหรือรอตรวจสอบ");
        }
        var media = paymentFiles.store(row.getId(), shop.getUuid(), actorId, file, PaymentFileService.PAYMENT_SLIP);
        row.attachProof(media.getId());
        subscriptions.save(row);
        adminNotifications.publishPlanPurchasePendingReview(row.getId(), shop.getId(), shop.getName());
        return toView(row, shop, true);
    }

    @Transactional
    public PurchaseOrderView cancel(Shop shop, UUID subscriptionId, UUID actorId) {
        var row = requireOwnedOpen(shop, subscriptionId);
        if (!row.isOpen()) {
            throw conflict("PLAN_PURCHASE_NOT_OPEN", "ยกเลิกได้เฉพาะคำสั่งซื้อที่ยังไม่ได้อนุมัติ");
        }
        row.cancel();
        subscriptions.save(row);
        log.info("Plan purchase cancelled sub={} shopNo={} by={}", row.getId(), shop.getId(), actorId);
        return toView(row, shop, false);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderView> adminList(String status) {
        var normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalized != null && !List.of(
            ShopPlanSubscription.PENDING_PAYMENT, ShopPlanSubscription.PENDING_REVIEW,
            ShopPlanSubscription.ACTIVE, ShopPlanSubscription.REJECTED,
            ShopPlanSubscription.CANCELLED, ShopPlanSubscription.EXPIRED
        ).contains(normalized)) {
            throw bad("PLAN_STATUS_INVALID", "สถานะที่กรองไม่ถูกต้อง");
        }
        var rows = normalized == null
            ? subscriptions.findAllByOrderByCreatedAtDesc()
            : subscriptions.findByStatusOrderByCreatedAtDesc(normalized);
        return rows.stream()
            .map(row -> {
                var shop = shops.findById(row.getShopId()).orElse(null);
                return shop == null ? null : toView(row, shop, false);
            })
            .filter(v -> v != null)
            .toList();
    }

    @Transactional
    public PurchaseOrderView approve(UUID subscriptionId, UUID actorId) {
        var row = lockSubscription(subscriptionId);
        if (ShopPlanSubscription.ACTIVE.equals(row.getStatus())) {
            throw conflict("PLAN_PURCHASE_ALREADY_APPROVED", "คำสั่งซื้อนี้ได้รับการอนุมัติแล้ว");
        }
        if (!ShopPlanSubscription.PENDING_REVIEW.equals(row.getStatus())
            && !ShopPlanSubscription.PENDING_PAYMENT.equals(row.getStatus())) {
            throw conflict("PLAN_PURCHASE_NOT_REVIEWABLE",
                "อนุมัติได้เฉพาะคำสั่งซื้อที่รอชำระเงินหรือรอตรวจสอบ (สถานะปัจจุบัน: " + row.getStatus() + ")");
        }
        var shop = shops.findById(row.getShopId())
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้าน"));
        var now = Instant.now();
        var start = computeStart(shop, row.getPlanCode(), now);
        var end = computeEnd(start, row.getBillingCycle());
        var beforePlan = shop.getPlanCode();
        // Audit + merchant inbox via existing assignPlan; then stamp billing/expiry.
        adminShops.assignPlan(shop.getId(), row.getPlanCode(), actorId);
        // Reload after assignPlan may have saved the shop.
        shop = shops.findByShopId(shop.getId()).orElse(shop);
        shop.assignPlanBilling(row.getBillingCycle(), end);
        shops.save(shop);
        if (java.util.Objects.equals(
            beforePlan == null ? null : beforePlan.trim().toUpperCase(Locale.ROOT),
            row.getPlanCode()
        )) {
            // Same-plan renewal: assignPlan skips notify when plan_code unchanged.
            merchantNotifications.publishShopPlanChanged(shop, row.getPlanCode(), now);
        }
        row.approve(actorId, start, end);
        subscriptions.save(row);
        return toView(row, shop, false);
    }

    @Transactional
    public PurchaseOrderView reject(UUID subscriptionId, String reason, UUID actorId) {
        var cleaned = reason == null ? "" : reason.trim();
        if (cleaned.isEmpty()) {
            throw badField("reason", "REJECT_REASON_REQUIRED",
                "กรุณาระบุเหตุผลที่ปฏิเสธในช่องเหตุผล เพื่อให้ร้านทราบว่าต้องแก้ไขอะไร");
        }
        if (cleaned.length() > 1000) {
            throw badField("reason", "REJECT_REASON_TOO_LONG", "เหตุผลยาวเกิน 1000 ตัวอักษร — ย่อให้สั้นลง");
        }
        var row = lockSubscription(subscriptionId);
        if (ShopPlanSubscription.ACTIVE.equals(row.getStatus())) {
            throw conflict("PLAN_PURCHASE_ALREADY_APPROVED", "คำสั่งซื้อนี้ได้รับการอนุมัติแล้ว ปฏิเสธไม่ได้");
        }
        if (!row.isOpen()) {
            throw conflict("PLAN_PURCHASE_NOT_REVIEWABLE", "ปฏิเสธได้เฉพาะคำสั่งซื้อที่ยังไม่ได้อนุมัติ");
        }
        var shop = shops.findById(row.getShopId())
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้าน"));
        row.reject(actorId, cleaned);
        subscriptions.save(row);
        merchantNotifications.publishShopPlanPurchaseRejected(shop, row.getId(), Instant.now());
        return toView(row, shop, false);
    }

    /** Downgrade expired paid plans to FREE; never touches products. */
    @Transactional
    public int expireDuePlans() {
        var now = Instant.now();
        var dueIds = jdbc.query("""
            SELECT shop_no FROM shops
             WHERE plan_expires_at IS NOT NULL AND plan_expires_at <= ?
               AND plan_code IS NOT NULL AND UPPER(TRIM(plan_code)) <> 'FREE'
            """, (rs, i) -> rs.getLong("shop_no"), java.sql.Timestamp.from(now));
        int n = 0;
        for (Long shopNo : dueIds) {
            var shop = shops.findByShopId(shopNo).orElse(null);
            if (shop == null) continue;
            shop.assignPlanCode(PlanQuotaService.DEFAULT_PLAN);
            shop.assignPlanBilling(null, null);
            shops.save(shop);
            subscriptions.findByShopIdOrderByCreatedAtDesc(shop.getUuid()).stream()
                .filter(s -> ShopPlanSubscription.ACTIVE.equals(s.getStatus()))
                .forEach(s -> {
                    s.expire();
                    subscriptions.save(s);
                });
            merchantNotifications.publishShopPlanChanged(shop, PlanQuotaService.DEFAULT_PLAN, now);
            merchantNotifications.publishShopPlanExpired(shop, now);
            n++;
        }
        return n;
    }

    @Transactional
    public int notifyExpiringSoon() {
        var now = ZonedDateTime.now(BANGKOK);
        int sent = 0;
        for (int days : List.of(7, 1)) {
            var dayStart = now.toLocalDate().plusDays(days).atStartOfDay(BANGKOK).toInstant();
            var dayEnd = now.toLocalDate().plusDays(days).plusDays(1).atStartOfDay(BANGKOK).toInstant();
            for (var shop : shops.findAll()) {
                var exp = shop.getPlanExpiresAt();
                if (exp == null || exp.isBefore(dayStart) || !exp.isBefore(dayEnd)) continue;
                if ("FREE".equals(effectivePlanCode(shop.getPlanCode()))) continue;
                merchantNotifications.publishShopPlanExpiringSoon(shop, days, dayStart);
                sent++;
            }
        }
        return sent;
    }

    Instant computeStart(Shop shop, String planCode, Instant now) {
        var current = effectivePlanCode(shop.getPlanCode());
        var exp = shop.getPlanExpiresAt();
        if (planCode.equalsIgnoreCase(current) && exp != null && exp.isAfter(now)) {
            return exp;
        }
        return now;
    }

    Instant computeEnd(Instant start, String cycle) {
        var z = start.atZone(BANGKOK);
        return (ShopPlanSubscription.YEARLY.equals(cycle) ? z.plusYears(1) : z.plusMonths(1)).toInstant();
    }

    private ShopPlanSubscription lockSubscription(UUID id) {
        UUID locked = jdbc.query(
            "SELECT id FROM shop_plan_subscriptions WHERE id = ? FOR UPDATE",
            rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
            id
        );
        if (locked == null) throw new AuthException(HttpStatus.NOT_FOUND, "PLAN_PURCHASE_NOT_FOUND", "ไม่พบคำสั่งซื้อแพ็กเกจ");
        return subscriptions.findById(id)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "PLAN_PURCHASE_NOT_FOUND", "ไม่พบคำสั่งซื้อแพ็กเกจ"));
    }

    private ShopPlanSubscription requireOwnedOpen(Shop shop, UUID id) {
        return subscriptions.findByIdAndShopId(id, shop.getUuid())
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "PLAN_PURCHASE_NOT_FOUND", "ไม่พบคำสั่งซื้อแพ็กเกจของร้านนี้"));
    }

    private List<ShopPlan> loadActivePlans() {
        return entities.createQuery(
            "select p from ShopPlan p where p.active = true order by p.sortOrder asc, p.code asc",
            ShopPlan.class
        ).getResultList();
    }

    private ShopPlan requireActivePlan(String code) {
        return loadActivePlans().stream()
            .filter(p -> code.equalsIgnoreCase(p.getCode()))
            .findFirst()
            .orElseThrow(() -> bad("PLAN_NOT_FOUND", "ไม่พบแพ็กเกจ " + code + " หรือแพ็กเกจนี้ปิดใช้งานแล้ว"));
    }

    private PurchaseCatalogPlan toCatalogPlan(ShopPlan plan) {
        var monthly = nz(plan.getPriceMonthly());
        var yearly = nz(plan.getPriceYearly());
        var savings = monthly.multiply(BigDecimal.valueOf(12)).subtract(yearly).max(BigDecimal.ZERO);
        BigDecimal pct = BigDecimal.ZERO;
        var yearFull = monthly.multiply(BigDecimal.valueOf(12));
        if (yearFull.compareTo(BigDecimal.ZERO) > 0 && savings.compareTo(BigDecimal.ZERO) > 0) {
            pct = savings.multiply(BigDecimal.valueOf(100)).divide(yearFull, 0, RoundingMode.HALF_UP);
        }
        var avg = yearly.compareTo(BigDecimal.ZERO) > 0
            ? yearly.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        return new PurchaseCatalogPlan(
            plan.getCode(),
            plan.getName(),
            plan.getDescription(),
            plan.getSortOrder(),
            "BASIC".equalsIgnoreCase(plan.getCode()),
            new PlanLimits(plan.getLimitProducts(), plan.getLimitOrdersPerMonth(), plan.getLimitStaff(), plan.getLimitStorageBytes()),
            monthly,
            yearly,
            savings,
            pct,
            avg
        );
    }

    private PurchaseOrderView toView(ShopPlanSubscription row, Shop shop, boolean includeTransfer) {
        var planName = loadActivePlans().stream()
            .filter(p -> p.getCode().equalsIgnoreCase(row.getPlanCode()))
            .map(ShopPlan::getName)
            .findFirst()
            .orElse(row.getPlanCode());
        Instant estStart = null;
        Instant estEnd = null;
        if (row.isOpen() || ShopPlanSubscription.PENDING_PAYMENT.equals(row.getStatus())) {
            estStart = computeStart(shop, row.getPlanCode(), Instant.now());
            estEnd = computeEnd(estStart, row.getBillingCycle());
        }
        return new PurchaseOrderView(
            row.getId(),
            shop.getId(),
            shop.getName(),
            row.getPlanCode(),
            planName,
            row.getBillingCycle(),
            row.getAmount(),
            row.getCurrency(),
            row.getStatus(),
            row.getStartsAt(),
            row.getEndsAt(),
            estStart,
            estEnd,
            row.getPaymentProofFileId(),
            row.getRejectReason(),
            row.getCreatedAt(),
            row.getUpdatedAt(),
            row.getReviewedAt(),
            includeTransfer ? transferAccount() : null
        );
    }

    private TransferAccount transferAccount() {
        return new TransferAccount(
            blank(billing.getDisplayName()),
            blank(billing.getBankName()),
            blank(billing.getAccountName()),
            blank(billing.getAccountNumber()),
            blank(billing.getPromptpay())
        );
    }

    private static BigDecimal priceFor(ShopPlan plan, String cycle) {
        return ShopPlanSubscription.YEARLY.equals(cycle) ? nz(plan.getPriceYearly()) : nz(plan.getPriceMonthly());
    }

    private static String requireBillingCycle(String raw) {
        if (raw == null || raw.isBlank()) {
            throw badField("billingCycle", "BILLING_CYCLE_REQUIRED",
                "กรุณาเลือกรอบบิลรายเดือนหรือรายปีก่อนสั่งซื้อ");
        }
        var cycle = raw.trim().toUpperCase(Locale.ROOT);
        if (!ShopPlanSubscription.MONTHLY.equals(cycle) && !ShopPlanSubscription.YEARLY.equals(cycle)) {
            throw badField("billingCycle", "BILLING_CYCLE_INVALID",
                "รอบบิลต้องเป็น MONTHLY หรือ YEARLY เท่านั้น");
        }
        return cycle;
    }

    private static String requirePaidPlanCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw badField("planCode", "PLAN_CODE_REQUIRED", "กรุณาเลือกแพ็กเกจที่ต้องการซื้อ");
        }
        var code = raw.trim().toUpperCase(Locale.ROOT);
        if ("FREE".equals(code)) {
            throw badField("planCode", "PLAN_FREE_NOT_PURCHASABLE",
                "แพ็กเกจ Free ไม่สามารถซื้อได้ — เลือก Basic หรือ Pro");
        }
        return code;
    }

    private static String effectivePlanCode(String planCode) {
        return planCode == null || planCode.isBlank() ? PlanQuotaService.DEFAULT_PLAN : planCode.trim().toUpperCase(Locale.ROOT);
    }

    private static String cycleLabel(String cycle) {
        return ShopPlanSubscription.YEARLY.equals(cycle) ? "รายปี" : "รายเดือน";
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static AuthException bad(String code, String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static AuthException badField(String field, String code, String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, code, message, field);
    }

    private static AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }
}
