package com.thaishopfun.auth.authz;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.AuthProperties;
import com.thaishopfun.auth.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.UUID;

/**
 * Blocks authenticated callers whose email is not verified from using protected APIs.
 * Login / register / refresh still issue sessions; only subsequent business APIs are gated.
 */
@Component
public class EmailNotVerifiedInterceptor implements HandlerInterceptor {
    private static final Set<String> EXEMPT = Set.of(
        "GET /api/auth/me",
        "POST /api/auth/logout",
        "POST /api/auth/refresh",
        "POST /api/auth/otp/send",
        "POST /api/auth/otp/verify",
        "POST /api/auth/otp/verify-link"
    );

    private final UserAccountRepository users;
    private final AuthProperties authProperties;

    public EmailNotVerifiedInterceptor(UserAccountRepository users, AuthProperties authProperties) {
        this.users = users;
        this.authProperties = authProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!authProperties.emailVerificationRequired()) return true;
        if (HttpMethod.OPTIONS.matches(request.getMethod())) return true;

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return true;
        }

        var key = request.getMethod().toUpperCase() + " " + normalizePath(request);
        if (EXEMPT.contains(key)) return true;

        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException ignored) {
            return true;
        }

        var user = users.findById(userId).orElse(null);
        if (user == null || user.isDeleted() || user.isEmailVerified()) return true;

        throw new AuthException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "กรุณายืนยันอีเมลก่อนใช้งาน");
    }

    private static String normalizePath(HttpServletRequest request) {
        var uri = request.getRequestURI();
        var context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        if (uri.length() > 1 && uri.endsWith("/")) uri = uri.substring(0, uri.length() - 1);
        return uri;
    }
}
