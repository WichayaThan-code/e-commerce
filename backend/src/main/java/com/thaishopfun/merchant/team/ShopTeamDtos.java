package com.thaishopfun.merchant.team;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ShopTeamDtos {
    private ShopTeamDtos() {}

    public record PageMeta(int page, int size, long totalItems, int totalPages) {}

    public record MemberSummary(
        UUID userId,
        String firstName,
        String lastName,
        String email,
        String role,
        String roleName,
        String status,
        Instant joinedAt,
        boolean active
    ) {}

    public record MemberPage(List<MemberSummary> items, PageMeta page, long totalActive) {}

    public record MemberDetail(
        UUID userId,
        String firstName,
        String lastName,
        String email,
        String role,
        String roleName,
        String status,
        Instant joinedAt,
        Instant removedAt,
        List<String> permissions,
        List<PermissionGroup> permissionGroups,
        List<MemberEvent> events
    ) {}

    public record MemberEvent(String action, String note, Instant at) {}

    public record InvitationSummary(
        UUID id,
        String email,
        String role,
        String roleName,
        String status,
        Instant expiresAt,
        Instant createdAt,
        Instant acceptedAt,
        String invitedByName,
        /** True when status is effectively PENDING (not expired) — resend/revoke allowed. */
        boolean actionable
    ) {}

    public record InvitationPage(List<InvitationSummary> items, PageMeta page, long totalPending) {}

    public record InvitationCreated(
        UUID id,
        String email,
        String role,
        String status,
        Instant expiresAt,
        /** Present only once on create/resend — never stored or listed again. */
        String inviteUrl,
        boolean emailQueued,
        String deliveryNote
    ) {}

    public record CreateInviteRequest(String email, String role) {}

    public record ChangeRoleRequest(String role) {}

    public record RemoveMemberRequest(String reason) {}

    public record RoleOption(
        String code,
        String nameTh,
        String nameEn,
        boolean assignableByActor,
        List<String> permissions,
        List<PermissionGroup> permissionGroups,
        boolean sensitive
    ) {}

    public record PermissionGroup(String module, List<PermissionItem> items) {}

    public record PermissionItem(String code, String descriptionTh, boolean sensitive) {}

    /**
     * @param staffLimit null when shops.plan_code is unset or plan.limit_staff is null (quota not enforced)
     * @param staffUsed ACTIVE members + PENDING invites that have not expired
     * @param quotaEnforced true only when staffLimit is non-null
     * @param quotaNote human-readable limitation when not enforced
     */
    public record StaffQuota(Long staffLimit, long staffUsed, boolean quotaEnforced, String quotaNote) {}

    public record RoleCatalog(List<RoleOption> roles, String actorRole, StaffQuota staffQuota) {}

    public record InvitePreview(
        String shopName,
        String inviterName,
        String role,
        String roleName,
        String status,
        Instant expiresAt,
        List<String> permissions,
        List<PermissionGroup> permissionGroups,
        boolean emailMatchesCaller,
        boolean callerAuthenticated,
        boolean callerEmailVerified,
        String conflictCode
    ) {}
}
