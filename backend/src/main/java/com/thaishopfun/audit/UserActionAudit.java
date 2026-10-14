package com.thaishopfun.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Writes one row to the existing {@code audit_logs} table for each user action that changes data,
 * and for any request the server rejects. Successful reads are omitted so status polling does not
 * bury the actions that matter.
 */
@Service
public class UserActionAudit {
    private static final Logger log = LoggerFactory.getLogger(UserActionAudit.class);
    private static final Pattern UUID_IN_PATH = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final JdbcTemplate jdbc;

    public UserActionAudit(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(HttpServletRequest request, HttpServletResponse response) {
        var method = request.getMethod();
        var path = request.getRequestURI();
        if (method == null || path == null || !path.startsWith("/api/") || path.startsWith("/error")) return;
        var status = response.getStatus();
        var read = "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
        if (read && status < 400) return;
        if ("GET".equals(method) && path.matches(".*/products/import/[^/]+")) return;
        var actor = actorId();
        var now = Timestamp.from(Instant.now());
        var action = limit(method + " " + path, 80);
        var resourceId = resourceId(path);
        try {
            jdbc.update("""
                insert into audit_logs (
                    occurred_at, actor_id, actor_type, action, resource_type, resource_id,
                    outcome, reason_code, auth_context, created_date_time, created_by, modified_date_time
                ) values (?, ?, 'USER', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                now, actor, action, resourceType(path), resourceId, outcome(status), "HTTP_" + status, context(path, actor), now, actor, now
            );
        } catch (RuntimeException exception) {
            log.warn("Could not write audit_logs for {} {}", method, path, exception);
        }
    }

    private static String outcome(int status) {
        if (status >= 200 && status < 300) return "SUCCESS";
        if (status == 401 || status == 403) return "DENIED";
        return "FAILURE";
    }

    private static String context(String path, UUID actor) {
        if (path.contains("/admin/")) return "PLATFORM";
        if (path.contains("/shops/") || path.contains("/merchant")) return "SHOP";
        return actor == null ? "PUBLIC" : "ACCOUNT";
    }

    private static String resourceType(String path) {
        var parts = path.split("/");
        var resource = "API";
        for (var index = parts.length - 1; index >= 0; index--) {
            var part = parts[index];
            if (part.isBlank() || part.chars().allMatch(Character::isDigit) || UUID_IN_PATH.matcher(part).matches()) continue;
            resource = part.toUpperCase(Locale.ROOT).replace('-', '_');
            break;
        }
        return limit(resource, 40);
    }

    private static UUID resourceId(String path) {
        var matcher = UUID_IN_PATH.matcher(path);
        UUID found = null;
        while (matcher.find()) found = UUID.fromString(matcher.group());
        return found;
    }

    private static UUID actorId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            try {
                return UUID.fromString(jwt.getSubject());
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
