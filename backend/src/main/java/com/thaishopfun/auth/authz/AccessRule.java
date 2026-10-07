package com.thaishopfun.auth.authz;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;

/** The access rule declared on a controller endpoint (method-level annotation wins over class-level). */
public record AccessRule(Kind kind, String permission, String shopParam, boolean defaultShop) {
    public enum Kind { PUBLIC, ACCOUNT, SHOP, SHOP_FILTER }

    /** Only this application's controllers are governed (framework handlers such as /error are not). */
    public static final String APPLICATION_PACKAGE = "com.thaishopfun.";

    public static boolean governs(HandlerMethod handler) {
        return handler.getBeanType().getName().startsWith(APPLICATION_PACKAGE);
    }

    /**
     * @return the declared rule, or {@code null} when the endpoint declares none (deny by default)
     * @throws IllegalStateException when more than one rule is declared on the same level
     */
    public static AccessRule resolve(HandlerMethod handler) {
        var onMethod = declared(handler.getMethod());
        if (!onMethod.isEmpty()) return single(onMethod, handler);
        var onClass = declared(handler.getBeanType());
        if (!onClass.isEmpty()) return single(onClass, handler);
        return null;
    }

    private static AccessRule single(List<AccessRule> rules, HandlerMethod handler) {
        if (rules.size() > 1) {
            throw new IllegalStateException("Endpoint " + handler.getShortLogMessage() + " declares more than one access rule: " + rules);
        }
        return rules.get(0);
    }

    private static List<AccessRule> declared(AnnotatedElement element) {
        var rules = new ArrayList<AccessRule>();
        if (AnnotatedElementUtils.hasAnnotation(element, PublicEndpoint.class)) {
            rules.add(new AccessRule(Kind.PUBLIC, null, null, false));
        }
        var account = AnnotatedElementUtils.findMergedAnnotation(element, RequirePermission.class);
        if (account != null) rules.add(new AccessRule(Kind.ACCOUNT, account.value(), null, false));
        var shop = AnnotatedElementUtils.findMergedAnnotation(element, RequireShopPermission.class);
        if (shop != null) rules.add(new AccessRule(Kind.SHOP, shop.value(), shop.shopParam(), shop.defaultShop()));
        var filter = AnnotatedElementUtils.findMergedAnnotation(element, FilterByShopPermission.class);
        if (filter != null) rules.add(new AccessRule(Kind.SHOP_FILTER, filter.value(), filter.shopParam(), false));
        return rules;
    }
}
