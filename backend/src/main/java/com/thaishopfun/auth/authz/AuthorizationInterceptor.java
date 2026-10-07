package com.thaishopfun.auth.authz;

import com.thaishopfun.auth.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces the access rule declared on every controller endpoint before the controller runs (deny by default).
 * Spring Security has already authenticated the JWT (signature, expiry, "pv" = users.permission_version) and
 * rejected anonymous calls to non-public endpoints with 401; this interceptor decides 403 / 404 from the
 * permission matrix in the database.
 */
@Component
public class AuthorizationInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AuthorizationInterceptor.class);
    private static final AccessRule MISSING = new AccessRule(null, null, null, false);

    private final AuthorizationService authorization;
    private final Map<Method, AccessRule> rules = new ConcurrentHashMap<>();

    public AuthorizationInterceptor(AuthorizationService authorization) {
        this.authorization = authorization;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !AccessRule.governs(method)) return true;
        var rule = rules.computeIfAbsent(method.getMethod(), key -> resolve(method));
        if (rule == MISSING) {
            log.error("Endpoint {} has no access rule; request denied (add @PublicEndpoint / @RequirePermission / @RequireShopPermission / @FilterByShopPermission)",
                method.getShortLogMessage());
            throw new AuthException(HttpStatus.FORBIDDEN, "ACCESS_RULE_MISSING", "API นี้ยังไม่ได้กำหนดสิทธิ์การเข้าถึง");
        }
        switch (rule.kind()) {
            case PUBLIC -> { return true; }
            case ACCOUNT -> {
                var userId = currentUser();
                if (!authorization.hasPermission(userId, rule.permission())) throw denied(rule.permission());
            }
            case SHOP -> {
                var userId = currentUser();
                var shopNo = shopParameter(request, rule.shopParam())
                    .or(() -> rule.defaultShop() ? defaultShop(userId) : Optional.empty())
                    .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ข้อมูลที่จำเป็นไม่ครบถ้วน"));
                requireShop(userId, shopNo, rule.permission());
            }
            case SHOP_FILTER -> {
                var userId = currentUser();
                shopParameter(request, rule.shopParam()).ifPresent(shopNo -> requireShop(userId, shopNo, rule.permission()));
            }
        }
        return true;
    }

    private AccessRule resolve(HandlerMethod method) {
        try {
            var rule = AccessRule.resolve(method);
            return rule == null ? MISSING : rule;
        } catch (IllegalStateException exception) {
            log.error(exception.getMessage());
            return MISSING;
        }
    }

    private void requireShop(UUID userId, Long shopNo, String permission) {
        switch (authorization.checkShop(userId, shopNo, permission)) {
            case GRANTED -> { }
            case DENIED -> throw denied(permission);
            case NOT_MEMBER -> throw new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้านี้");
        }
    }

    private Optional<Long> defaultShop(UUID userId) {
        var shop = authorization.defaultShop(userId);
        if (shop.isEmpty()) throw new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ยังไม่มีร้านค้าที่สมัครไว้");
        return shop;
    }

    private static Optional<Long> shopParameter(HttpServletRequest request, String name) {
        String raw = null;
        if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables
            && variables.get(name) instanceof String value) {
            raw = value;
        }
        if (raw == null) raw = request.getParameter(name);
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(Long.parseLong(raw.trim()));
        } catch (NumberFormatException exception) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ค่าพารามิเตอร์ " + name + " ไม่ถูกต้อง");
        }
    }

    private static UUID currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            try {
                return UUID.fromString(jwt.getSubject());
            } catch (RuntimeException ignored) {
                // falls through to 401
            }
        }
        throw new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "กรุณาเข้าสู่ระบบ");
    }

    private static AuthException denied(String permission) {
        return new AuthException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "คุณไม่มีสิทธิ์ทำรายการนี้ (ต้องมีสิทธิ์ " + permission + ")");
    }
}
