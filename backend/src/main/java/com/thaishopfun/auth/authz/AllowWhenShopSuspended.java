package com.thaishopfun.auth.authz;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exempts one shop-scoped write endpoint from the suspended-shop freeze in {@link AuthorizationInterceptor}
 * (e.g. the merchant's reinstatement request). Reads (GET / HEAD / OPTIONS) are never frozen.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AllowWhenShopSuspended {
}
