package com.thaishopfun.auth.authz;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Access rule: the endpoint is reachable without signing in (login, register, public catalog ...).
 * Every controller endpoint must carry exactly one access rule ({@link PublicEndpoint}, {@link RequirePermission},
 * {@link RequireShopPermission} or {@link FilterByShopPermission}) on the method or its class; endpoints without one
 * are denied (deny by default) and fail {@code EndpointAccessRuleCoverageTests}.
 * <p>{@link PublicEndpointRegistry} turns these annotations into Spring Security permitAll matchers, so the
 * annotation is the single source of truth for "public".
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface PublicEndpoint {
    /** Why this endpoint is public (documentation only). */
    String value() default "";
}
