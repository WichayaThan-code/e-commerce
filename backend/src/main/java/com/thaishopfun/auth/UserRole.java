package com.thaishopfun.auth;

/**
 * Kind of account shown to the frontend (labels, landing page, redirects). NOT stored any more: the legacy
 * users.role column was dropped in Flyway V18. It is derived from the role tables on every response, with the same
 * rule the frontend uses ({@code accountKind} in services/permissions.ts):
 * <ul>
 *   <li>ADMIN: an active platform role (platform_roles, revoked_at IS NULL), i.e. any PLATFORM_* permission;</li>
 *   <li>MERCHANT: otherwise, an active membership in at least one shop (shop_members ACTIVE, removed_at IS NULL);</li>
 *   <li>CUSTOMER: everyone else.</li>
 * </ul>
 * Never used for authorization: permissions come from the permission matrix (AuthorizationService).
 */
public enum UserRole {
    CUSTOMER, MERCHANT, ADMIN;

    public static UserRole derive(boolean platformAccess, boolean shopMember) {
        return platformAccess ? ADMIN : shopMember ? MERCHANT : CUSTOMER;
    }
}
