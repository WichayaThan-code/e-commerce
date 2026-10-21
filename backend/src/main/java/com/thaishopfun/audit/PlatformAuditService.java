package com.thaishopfun.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * Central writer of business audit rows for platform admin writes (V1 audit_logs + V31 reason / ip / user agent):
 * actor, action, target (resource type + id, shop), before / after (non-sensitive fields only), reason code and
 * free-text reason, client IP, user agent, request id and time. Runs inside the caller's transaction, so the audit
 * row commits or rolls back together with the change it describes.
 */
@Service
public class PlatformAuditService {
    private final PlatformAuditLogRepository logs;

    public PlatformAuditService(PlatformAuditLogRepository logs) {
        this.logs = logs;
    }

    /** One audit row. before / after must not contain secrets or PII (status, ids, codes, counts only). */
    public record Entry(
        UUID actorId, String action, String resourceType, UUID resourceId, UUID shopId, String permission,
        String outcome, String reasonCode, String reason, Map<String, Object> before, Map<String, Object> after,
        String authContext, String policyVersion
    ) {
        public static Entry platform(UUID actorId, String action, String resourceType, UUID resourceId) {
            return new Entry(actorId, action, resourceType, resourceId, null, null, "SUCCESS", null, null, null, null, "PLATFORM", null);
        }
        public Entry shop(UUID value) { return new Entry(actorId, action, resourceType, resourceId, value, permission, outcome, reasonCode, reason, before, after, authContext, policyVersion); }
        public Entry permission(String value) { return new Entry(actorId, action, resourceType, resourceId, shopId, value, outcome, reasonCode, reason, before, after, authContext, policyVersion); }
        public Entry reason(String code, String text) { return new Entry(actorId, action, resourceType, resourceId, shopId, permission, outcome, code, text, before, after, authContext, policyVersion); }
        public Entry change(Map<String, Object> from, Map<String, Object> to) { return new Entry(actorId, action, resourceType, resourceId, shopId, permission, outcome, reasonCode, reason, from, to, authContext, policyVersion); }
        public Entry policy(String value) { return new Entry(actorId, action, resourceType, resourceId, shopId, permission, outcome, reasonCode, reason, before, after, authContext, value); }
    }

    @Transactional
    public PlatformAuditLog record(Entry entry) {
        var request = currentRequest();
        String ip = null, agent = null, requestId = null;
        if (request != null) {
            ip = limit(request.getRemoteAddr(), 64);
            agent = limit(request.getHeader("User-Agent"), 500);
            requestId = limit(request.getHeader("X-Request-Id"), 64);
        }
        return logs.save(new PlatformAuditLog(entry, requestId, ip, agent));
    }

    /** IP / user agent of the current HTTP request (also used by the HTTP-level {@link UserActionAudit}). */
    static String[] client(HttpServletRequest request) {
        return new String[]{limit(request.getRemoteAddr(), 64), limit(request.getHeader("User-Agent"), 500)};
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes ? attributes.getRequest() : null;
    }

    static String limit(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
