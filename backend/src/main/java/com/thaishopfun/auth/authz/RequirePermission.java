package com.thaishopfun.auth.authz;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Access rule: the signed-in user must hold this permission at account/platform level, i.e. through an
 * account-context role every active account has (auth_roles.context ACCOUNT / APPLICANT / PUBLIC, e.g. BUYER,
 * APPLICANT) or through an active {@code platform_roles} row (e.g. PLATFORM_ADMIN). The role to permission
 * mapping comes from {@code auth_role_permissions}. Use a permission whose auth_permissions.scope is not STORE.
 * Missing permission: 403 PERMISSION_DENIED. Not signed in / bad token: 401.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequirePermission {
    /** Permission code from auth_permissions.code, e.g. PLATFORM_APPLICATION_REVIEW. */
    String value();
}
