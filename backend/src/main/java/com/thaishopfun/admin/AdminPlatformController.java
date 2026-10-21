package com.thaishopfun.admin;

import com.thaishopfun.admin.AdminPlatformDtos.*;
import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Platform admin API: merchants, shops, plans & usage, audit logs. Every endpoint is a PLATFORM_* account-level
 * permission (platform_roles only; shop roles never grant it). Merchant / shop suspend and reactivate need a reason,
 * are idempotent (changed = false on a repeat) and answer 409 on an invalid status.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminPlatformController {
    private final AdminMerchantService merchants;
    private final AdminShopService shops;
    private final AdminShopUsageService usage;
    private final AdminAuditLogService audits;

    public AdminPlatformController(AdminMerchantService merchants, AdminShopService shops, AdminShopUsageService usage, AdminAuditLogService audits) {
        this.merchants = merchants;
        this.shops = shops;
        this.usage = usage;
        this.audits = audits;
    }

    // ---------------- merchants ----------------

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping("/merchants")
    public Page<MerchantRow> merchants(
        @RequestParam(required = false) String q, @RequestParam(required = false) String status,
        @RequestParam(required = false) String ownerType, @RequestParam(required = false) String shopStatus,
        @RequestParam(required = false) UUID userId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "createdAt") String sort, @RequestParam(defaultValue = "desc") String dir,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return merchants.list(new AdminMerchantService.Filter(q, status, ownerType, shopStatus, userId, from, to), sort, dir, page, size);
    }

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping("/merchants/{merchantId}")
    public MerchantDetail merchant(@PathVariable UUID merchantId) {
        return merchants.detail(merchantId);
    }

    @RequirePermission("PLATFORM_SHOP_SUSPEND")
    @PostMapping("/merchants/{merchantId}/suspend")
    public MerchantActionResponse suspendMerchant(@PathVariable UUID merchantId, @Valid @RequestBody ReasonRequest body, @AuthenticationPrincipal Jwt jwt) {
        return merchants.suspend(merchantId, body, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_SHOP_RESTORE")
    @PostMapping("/merchants/{merchantId}/reactivate")
    public MerchantActionResponse reactivateMerchant(@PathVariable UUID merchantId, @Valid @RequestBody ReasonRequest body, @AuthenticationPrincipal Jwt jwt) {
        return merchants.reactivate(merchantId, body, RequestUser.id(jwt));
    }

    // ---------------- shops ----------------

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping("/shops")
    public Page<ShopRow> shops(
        @RequestParam(required = false) String q, @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID merchantId, @RequestParam(required = false) UUID ownerUserId,
        @RequestParam(required = false) String category, @RequestParam(required = false) String province,
        @RequestParam(required = false) String suspendReasonCode,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "createdAt") String sort, @RequestParam(defaultValue = "desc") String dir,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return shops.list(new AdminShopService.Filter(q, status, merchantId, ownerUserId, category, province, suspendReasonCode, from, to), sort, dir, page, size);
    }

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping("/shops/{shopId}")
    public ShopDetail shop(@PathVariable Long shopId) {
        return shops.detail(shopId);
    }

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping("/shops/{shopId}/usage")
    public ShopUsage shopUsage(@PathVariable Long shopId) {
        return shops.usage(shopId);
    }

    @RequirePermission("PLATFORM_SHOP_SUSPEND")
    @PostMapping("/shops/{shopId}/suspend")
    public ShopActionResponse suspendShop(@PathVariable Long shopId, @Valid @RequestBody ReasonRequest body, @AuthenticationPrincipal Jwt jwt) {
        return shops.suspend(shopId, body, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_SHOP_RESTORE")
    @PostMapping("/shops/{shopId}/reactivate")
    public ShopActionResponse reactivateShop(@PathVariable Long shopId, @Valid @RequestBody ReasonRequest body, @AuthenticationPrincipal Jwt jwt) {
        return shops.reactivate(shopId, body, RequestUser.id(jwt));
    }

    // ---------------- plans & usage ----------------

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping("/plans")
    public PlanCatalog plans() {
        return usage.plans();
    }

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping("/plans/usage")
    public Page<ShopUsage> planUsage(
        @RequestParam(required = false) String q, @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID merchantId,
        @RequestParam(defaultValue = "createdAt") String sort, @RequestParam(defaultValue = "desc") String dir,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return shops.usagePage(new AdminShopService.Filter(q, status, merchantId, null, null, null, null, null, null), sort, dir, page, size);
    }

    // ---------------- audit logs ----------------

    @RequirePermission("PLATFORM_AUDIT_READ")
    @GetMapping("/audit-logs")
    public Page<AuditLogView> auditLogs(
        @RequestParam(required = false) UUID actorId, @RequestParam(required = false) String action,
        @RequestParam(required = false) String resourceType, @RequestParam(required = false) UUID resourceId,
        @RequestParam(required = false) Long shopId, @RequestParam(required = false) String outcome,
        @RequestParam(required = false) String authContext, @RequestParam(required = false) String reasonCode,
        @RequestParam(required = false) String kind, @RequestParam(required = false) String q,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "desc") String dir,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return audits.list(new AdminAuditLogService.Filter(actorId, action, resourceType, resourceId, shopId, outcome, authContext,
            reasonCode, kind, q, from, to), dir, page, size);
    }

    @RequirePermission("PLATFORM_AUDIT_READ")
    @GetMapping("/audit-logs/{id}")
    public AuditLogView auditLog(@PathVariable Long id) {
        return audits.detail(id);
    }
}
