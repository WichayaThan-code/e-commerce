package com.thaishopfun.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read models of the admin "users" and "roles & permissions" pages (all values come from the database). */
public final class AdminDirectoryDtos {
    private AdminDirectoryDtos() {}

    /** Synthetic role filter: accounts without any platform role or active shop membership (plain buyers). */
    public static final String GENERAL = "GENERAL";

    public record RoleRef(String code, String nameTh, String context) {}

    public record ShopRef(Long shopId, String shopName, String shopStatus, String role, String roleNameTh) {}

    public record UserRow(
        UUID id, String firstName, String lastName, String email, String phone,
        String status, String accountRole, RoleRef primaryRole, List<String> roleCodes, List<ShopRef> shops,
        boolean superAdmin, Instant createdAt, Instant lastLoginAt
    ) {}

    public record UserPage(List<UserRow> items, int page, int size, long totalItems, int totalPages) {}

    /** A KPI value with the value at the end of the previous month; changePercent is null when there is no baseline. */
    public record Metric(long value, Long previous, Double changePercent) {}

    public record UserStats(
        Metric total, Metric admins, Metric storeOwners, Metric generalUsers,
        long active, long suspended, int roleCount, Instant previousPeriodEnd, Instant generatedAt
    ) {}

    public record FilterOption(String value, String label, long count) {}

    public record UserFilterOptions(List<FilterOption> roles, List<FilterOption> statuses) {}

    public record PermissionInfo(String code, String module, String scope, String descriptionTh, boolean active) {}

    public record ShopAccess(Long shopId, String shopName, String shopStatus, String role, String roleNameTh, Instant joinedAt, List<PermissionInfo> permissions) {}

    public record UserDetail(
        UUID id, String firstName, String lastName, String email, String phone, String status, String accountRole,
        boolean superAdmin, Instant createdAt, Instant lastLoginAt, Instant emailVerifiedAt, Instant phoneVerifiedAt,
        RoleRef primaryRole, List<RoleRef> roles, List<PermissionInfo> accountPermissions, List<ShopAccess> shops
    ) {}

    /** One line of a history / recent-activity feed, built from audit_logs and the role/membership tables. */
    public record ActivityEvent(
        Instant at, String type, String title, String subjectName, String actorName,
        String roleCode, String roleNameTh, String shopName, String source
    ) {}

    public record RoleSummary(
        String code, String nameTh, String descriptionTh, String context, boolean assignable, int sortOrder,
        String policyVersion, String assignment, Long userCount, int permissionCount, boolean active, boolean superAdmin
    ) {}

    public record PermissionGroup(String module, List<PermissionInfo> permissions) {}

    public record RoleDetail(RoleSummary role, List<PermissionGroup> groups, int catalogPermissionCount) {}

    public record ShopOption(Long shopId, String shopName, String status) {}

    public record ShopRoleMember(
        UUID userId, String firstName, String lastName, String email, String role, String roleNameTh,
        boolean active, Instant joinedAt
    ) {}
}
