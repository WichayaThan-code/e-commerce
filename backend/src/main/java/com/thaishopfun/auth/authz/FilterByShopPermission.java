package com.thaishopfun.auth.authz;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Access rule for "my shops" style endpoints that any signed-in user may call: the response only contains shops
 * in which the caller holds this STORE-scope permission (a user without shops gets an empty result, not 403).
 * The service filters with {@link AuthorizationService#shopsWithPermission}. If the request does carry
 * {@link #shopParam()}, that shop is checked exactly like {@link RequireShopPermission} (404 / 403).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface FilterByShopPermission {
    /** STORE-scope permission code used to filter the result, e.g. SHOP_READ. */
    String value();

    /** Optional request parameter that narrows the call to one shop. */
    String shopParam() default "shopId";
}
