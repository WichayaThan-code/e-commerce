package com.thaishopfun.merchant;

import com.thaishopfun.audit.PlatformAuditService;
import com.thaishopfun.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Platform admin suspend / reactivate of shops and merchants (owners), shared by the new /api/admin/shops and
 * /api/admin/merchants endpoints and the pre-existing /api/admin/store-applications endpoints.
 * <ul>
 *   <li>Shop: APPROVED -> SUSPENDED fills the V1 columns suspended_at / suspended_by / suspend_reason /
 *       suspend_reason_code; SUSPENDED -> APPROVED fills restored_at / restored_by and approves a pending reinstatement
 *       request. Any other status is 409. With {@code idempotent} a repeated call is a no-op (changed = false).</li>
 *   <li>Merchant: owners.status (V31) ACTIVE <-> SUSPENDED. Suspending also suspends every APPROVED shop of the
 *       merchant with suspend_reason_code = MERCHANT_SUSPENDED; reactivating restores only those shops (shops that
 *       were suspended on their own stay suspended). A shop of a suspended merchant cannot be approved or reactivated
 *       on its own (409 MERCHANT_SUSPENDED).</li>
 * </ul>
 * Every change writes a business audit row (actor, action, target, before / after, reason, IP, user agent) in the
 * same transaction.
 */
@Service
public class ShopSuspensionService {
    public static final String MERCHANT_SUSPENDED = "MERCHANT_SUSPENDED";
    public static final String DEFAULT_SUSPEND_CODE = "ADMIN_SUSPEND";
    public static final String DEFAULT_RESTORE_CODE = "ADMIN_RESTORE";
    /** reason_code of changes made through the pre-existing endpoints when the caller sent no reason */
    public static final String LEGACY_NO_REASON = "NO_REASON_GIVEN";

    private final ShopRepository shops;
    private final ShopReinstatementService reinstatements;
    private final PlatformAuditService audit;

    public ShopSuspensionService(ShopRepository shops, ShopReinstatementService reinstatements, PlatformAuditService audit) {
        this.shops = shops;
        this.reinstatements = reinstatements;
        this.audit = audit;
    }

    /** @return true when the shop changed; false for a repeated call when idempotent */
    @Transactional
    public boolean suspendShop(Shop shop, UUID actorId, String reasonCode, String reason, boolean idempotent) {
        if (shop.getStatus() == ShopStatus.SUSPENDED && idempotent) return false;
        if (shop.getStatus() != ShopStatus.APPROVED) {
            throw new AuthException(HttpStatus.CONFLICT, "SHOP_NOT_APPROVED", "ระงับได้เฉพาะร้านที่อนุมัติแล้ว (สถานะปัจจุบัน: " + shop.getStatus() + ")");
        }
        var before = state(shop);
        shop.suspend(actorId, reason, reasonCode);
        record(actorId, "SHOP_SUSPENDED", shop, "PLATFORM_SHOP_SUSPEND", reasonCode, reason, before, null);
        return true;
    }

    @Transactional
    public boolean reactivateShop(Shop shop, UUID actorId, String reasonCode, String reason, boolean idempotent) {
        if (shop.getStatus() == ShopStatus.APPROVED && idempotent) return false;
        if (shop.getStatus() != ShopStatus.SUSPENDED) {
            throw new AuthException(HttpStatus.CONFLICT, "SHOP_NOT_SUSPENDED", "เปิดใช้งานได้เฉพาะร้านที่ถูกระงับ (สถานะปัจจุบัน: " + shop.getStatus() + ")");
        }
        requireMerchantActive(shop);
        var before = state(shop);
        shop.reactivate(actorId);
        reinstatements.approvePending(shop.getId());
        record(actorId, "SHOP_REACTIVATED", shop, "PLATFORM_SHOP_RESTORE", reasonCode, reason, before, null);
        return true;
    }

    /** 409 MERCHANT_SUSPENDED when the shop's merchant is suspended (approve / reactivate a single shop). */
    public void requireMerchantActive(Shop shop) {
        if (merchantSuspended(shop)) {
            throw new AuthException(HttpStatus.CONFLICT, MERCHANT_SUSPENDED, "ผู้ขายของร้านนี้ถูกระงับอยู่ ต้องเปิดใช้งานผู้ขายก่อน");
        }
    }

    public boolean merchantSuspended(Shop shop) {
        return shop.getOwner() != null && shop.getOwner().isSuspended();
    }

    /** @return shop numbers suspended together with the merchant, or null when the merchant was already suspended */
    @Transactional
    public List<Long> suspendMerchant(Owner owner, UUID actorId, String reasonCode, String reason) {
        if (owner.isSuspended()) return null;
        var before = merchantState(owner);
        var affected = new ArrayList<Long>();
        owner.suspend(actorId, reason, reasonCode);
        for (var shop : shops.findByOwner_IdOrderByShopIdAsc(owner.getId())) {
            if (shop.getStatus() != ShopStatus.APPROVED) continue;
            var shopBefore = state(shop);
            shop.suspend(actorId, reason, MERCHANT_SUSPENDED);
            record(actorId, "SHOP_SUSPENDED", shop, "PLATFORM_SHOP_SUSPEND", MERCHANT_SUSPENDED, reason, shopBefore, owner.getId());
            affected.add(shop.getId());
        }
        var after = merchantState(owner);
        after.put("cascadedShopIds", affected);
        audit.record(PlatformAuditService.Entry.platform(actorId, "MERCHANT_SUSPENDED", OwnerAuditLog.RESOURCE_TYPE, owner.getId())
            .permission("PLATFORM_SHOP_SUSPEND").reason(reasonCode, reason).change(before, after));
        return affected;
    }

    /** @return shop numbers restored together with the merchant, or null when the merchant was already active */
    @Transactional
    public List<Long> reactivateMerchant(Owner owner, UUID actorId, String reasonCode, String reason) {
        if (!owner.isSuspended()) return null;
        var before = merchantState(owner);
        var affected = new ArrayList<Long>();
        owner.reactivate(actorId);
        for (var shop : shops.findByOwner_IdOrderByShopIdAsc(owner.getId())) {
            if (shop.getStatus() != ShopStatus.SUSPENDED || !MERCHANT_SUSPENDED.equals(shop.getSuspendReasonCode())) continue;
            var shopBefore = state(shop);
            shop.reactivate(actorId);
            reinstatements.approvePending(shop.getId());
            record(actorId, "SHOP_REACTIVATED", shop, "PLATFORM_SHOP_RESTORE", MERCHANT_SUSPENDED, reason, shopBefore, owner.getId());
            affected.add(shop.getId());
        }
        var after = merchantState(owner);
        after.put("restoredShopIds", affected);
        audit.record(PlatformAuditService.Entry.platform(actorId, "MERCHANT_REACTIVATED", OwnerAuditLog.RESOURCE_TYPE, owner.getId())
            .permission("PLATFORM_SHOP_RESTORE").reason(reasonCode, reason).change(before, after));
        return affected;
    }

    /** Audit row for application review decisions (approve / reject) made through store-applications. */
    public void recordReview(UUID actorId, String action, Shop shop, ShopStatus before) {
        var from = new LinkedHashMap<String, Object>();
        from.put("status", before.name());
        record(actorId, action, shop, "PLATFORM_APPLICATION_REVIEW", "ADMIN_REVIEW", null, from, null);
    }

    private void record(UUID actorId, String action, Shop shop, String permission, String reasonCode, String reason,
                        Map<String, Object> before, UUID cascadeFromMerchant) {
        var after = state(shop);
        if (cascadeFromMerchant != null) after.put("cascadeFromMerchantId", cascadeFromMerchant.toString());
        audit.record(PlatformAuditService.Entry.platform(actorId, action, "SHOP", shop.getUuid())
            .shop(shop.getUuid()).permission(permission).reason(reasonCode, reason).change(before, after));
    }

    private static Map<String, Object> state(Shop shop) {
        var map = new LinkedHashMap<String, Object>();
        map.put("shopId", shop.getId());
        map.put("status", shop.getStatus().name());
        if (shop.getSuspendReasonCode() != null && shop.getStatus() == ShopStatus.SUSPENDED) map.put("suspendReasonCode", shop.getSuspendReasonCode());
        return map;
    }

    private static Map<String, Object> merchantState(Owner owner) {
        var map = new LinkedHashMap<String, Object>();
        map.put("status", owner.getStatus());
        return map;
    }
}
