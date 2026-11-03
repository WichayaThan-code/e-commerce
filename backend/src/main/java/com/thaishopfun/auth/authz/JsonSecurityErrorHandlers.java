package com.thaishopfun.auth.authz;

import com.thaishopfun.auth.DatabaseConnectivityFailures;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JSON {code,message} bodies for the 401 / 403 answers produced by Spring Security itself (same shape as
 * ApiExceptionHandler). 401 codes: UNAUTHORIZED (no token), TOKEN_EXPIRED, TOKEN_REVOKED (users.permission_version
 * changed, account suspended or deleted), TOKEN_INVALID (bad signature / malformed).
 */
public final class JsonSecurityErrorHandlers {
    private JsonSecurityErrorHandlers() {}

    public static AuthenticationEntryPoint authenticationEntryPoint() {
        var bearer = new BearerTokenAuthenticationEntryPoint();
        return (request, response, exception) -> {
            if (DatabaseConnectivityFailures.isConnectivityFailure(exception)) {
                write(response, HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "ฐานข้อมูลใช้งานไม่ได้");
                return;
            }
            bearer.commence(request, response, exception); // status 401 + WWW-Authenticate header
            var code = classify(request, exception);
            write(response, HttpStatus.UNAUTHORIZED, code, switch (code) {
                case "TOKEN_EXPIRED" -> "เซสชันหมดอายุ กรุณาเข้าสู่ระบบใหม่";
                case "TOKEN_REVOKED" -> "สิทธิ์การใช้งานมีการเปลี่ยนแปลง กรุณาเข้าสู่ระบบใหม่";
                case "TOKEN_INVALID" -> "โทเคนไม่ถูกต้อง กรุณาเข้าสู่ระบบใหม่";
                default -> "กรุณาเข้าสู่ระบบ";
            });
        };
    }

    public static AccessDeniedHandler accessDeniedHandler() {
        return (HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) ->
            write(response, HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "คุณไม่มีสิทธิ์ทำรายการนี้");
    }

    static String classify(HttpServletRequest request, AuthenticationException exception) {
        var header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return "UNAUTHORIZED";
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof JwtValidationException validation) {
                for (var error : validation.getErrors()) {
                    var description = error.getDescription() == null ? "" : error.getDescription();
                    if (description.contains("expired")) return "TOKEN_EXPIRED";
                }
                for (var error : validation.getErrors()) {
                    var description = error.getDescription() == null ? "" : error.getDescription();
                    if (description.contains("no longer valid")) return "TOKEN_REVOKED";
                }
            }
        }
        var message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.contains("expired")) return "TOKEN_EXPIRED";
        if (message.contains("no longer valid")) return "TOKEN_REVOKED";
        return "TOKEN_INVALID";
    }

    private static void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // code and message are fixed strings above (no user input), so plain concatenation is safe JSON.
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
