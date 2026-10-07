package com.thaishopfun.auth.authz;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Access rule for shop-scoped endpoints: the signed-in user must be an ACTIVE member ({@code shop_members}) of the
 * target shop and the member's role must grant this STORE-scope permission ({@code auth_role_permissions}).
 * The target shop is the public running number ({@code shops.shop_no}) read from the request parameter or path
 * variable named {@link #shopParam()}.
 * <ul>
 *   <li>not a member of that shop (or the shop does not exist): 404 SHOP_NOT_FOUND (existence is not revealed)</li>
 *   <li>member whose role lacks the permission: 403 PERMISSION_DENIED</li>
 *   <li>parameter missing (and {@link #defaultShop()} is false) or not a number: 400 VALIDATION_ERROR</li>
 * </ul>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireShopPermission {
    /** STORE-scope permission code from auth_permissions.code, e.g. SHOP_UPDATE. */
    String value();

    /** Name of the request parameter / path variable that carries the shop number. */
    String shopParam() default "shopId";

    /**
     * When the parameter is absent, use the caller's default shop: the membership with the highest shop number
     * (the same shop the services pick when shopId is omitted). No membership at all: 404 SHOP_NOT_FOUND.
     */
    boolean defaultShop() default false;
}
