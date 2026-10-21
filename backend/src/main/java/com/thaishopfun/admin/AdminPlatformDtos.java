package com.thaishopfun.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Platform admin read models / requests: merchants, shops, plans & usage, suspension, audit logs. */
public final class AdminPlatformDtos {
    private AdminPlatformDtos() {}

    public record Page<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}

    /** Mandatory reason for merchant / shop suspend and reactivate (reasonCode optional, defaults per action). */
    public record ReasonRequest(
        @NotBlank(message = "กรุณาระบุเหตุผล") @Size(min = 5, max = 1000, message = "เหตุผลต้องยาว 5-1000 ตัวอักษร") String reason,
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$", message = "reasonCode ต้องเป็นตัวพิมพ์ใหญ่ ตัวเลข หรือ _ (2-50 ตัว)") String reasonCode
    ) {}

    /** Optional reason on the pre-existing endpoints (users suspend / restore, store-applications suspend / reactivate). */
    public record OptionalReasonRequest(@Size(max = 1000, message = "เหตุผลยาวเกิน 1000 ตัวอักษร") String reason) {}

    public record Suspension(String status, Instant suspendedAt, UUID suspendedBy, String suspendedByName, String reasonCode,
                             String reason, Instant restoredAt, UUID restoredBy, String restoredByName) {}

    public record UserRef(UUID id, String firstName, String lastName, String email, String phone, String status) {}

    // ---------------- merchants (owners) ----------------

    public record MerchantRow(
        UUID merchantId, String ownerType, String displayName, String email, String phone, String province,
        String status, UserRef user, long shopCount, long approvedShops, long suspendedShops, long pendingShops,
        Instant createdAt, Instant suspendedAt
    ) {}

    public record MerchantShopRef(Long shopId, String name, String slug, String status, String suspendReasonCode, Instant createdAt) {}

    public record MerchantDetail(
        UUID merchantId, String ownerType, String title, String firstName, String lastName, String email, String phone,
        String province, String district, String sensitiveReview, String status, Suspension suspension, UserRef user,
        List<MerchantShopRef> shops, MerchantCounts counts, Instant createdAt, Instant updatedAt
    ) {}

    public record MerchantCounts(long shops, long approvedShops, long suspendedShops, long products, long orders, long activeStaff) {}

    public record MerchantActionResponse(boolean changed, MerchantDetail merchant, List<Long> affectedShopIds) {}

    // ---------------- shops ----------------

    public record ShopRow(
        Long shopId, String name, String nameEn, String slug, String status, String category, String province,
        UUID merchantId, String merchantName, String merchantStatus, UserRef owner, long activeMembers, long products, long orders,
        Instant createdAt, Instant publishedAt, Instant suspendedAt
    ) {}

    public record ShopMemberView(UUID userId, String firstName, String lastName, String email, String role, String roleNameTh,
                                 String status, Instant joinedAt, Instant removedAt) {}

    public record ShopDetail(
        Long shopId, String name, String nameEn, String slug, String status, String category, String description,
        String address, String province, String district, String subdistrict, String postalCode,
        UUID merchantId, String merchantName, String merchantStatus, UserRef owner, Suspension suspension,
        String pendingReinstatementMessage, List<ShopMemberView> members, ShopUsage usage,
        Instant createdAt, Instant updatedAt, Instant publishedAt
    ) {}

    public record ShopActionResponse(boolean changed, ShopDetail shop) {}

    // ---------------- plans & usage ----------------

    /** Limits of a plan; null = no limit recorded. */
    public record PlanLimits(Long products, Long ordersPerMonth, Long staff, Long storageBytes) {}

    public record Plan(String code, String name, PlanLimits limits) {}

    /** source = NONE: the schema has no plan / subscription tables, so no shop is on a plan and no limit applies. */
    public record PlanCatalog(String source, List<Plan> plans, String note) {}

    public record ProductUsage(long total, long published, long draft, long archived, long activeSkus, long images) {}

    public record OrderUsage(long total, long last30Days, long open, long completed, long cancelled, BigDecimal paidGrandTotal) {}

    public record StaffUsage(long active, Map<String, Long> byRole) {}

    public record StorageUsage(long mediaFiles, long mediaBytes, long documents, long documentBytes, long productImages,
                               long trackedBytes, String note) {}

    public record ShopUsage(
        Long shopId, String shopName, String status, String planCode, PlanLimits limits,
        ProductUsage products, OrderUsage orders, StaffUsage staff, long customers, long activeCoupons,
        StorageUsage storage, Instant computedAt
    ) {}

    // ---------------- audit logs ----------------

    public record AuditActor(UUID id, String type, String name, String email) {}

    public record AuditTarget(String type, UUID id, Long shopId, String shopName) {}

    public record AuditLogView(
        Long id, Instant occurredAt, AuditActor actor, String action, AuditTarget target, String permission,
        String outcome, String reasonCode, String reason, Map<String, Object> before, Map<String, Object> after,
        String ipAddress, String userAgent, String requestId, String authContext, String policyVersion, String kind
    ) {}
}
