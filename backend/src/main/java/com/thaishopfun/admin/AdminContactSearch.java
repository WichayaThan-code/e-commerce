package com.thaishopfun.admin;

import com.thaishopfun.audit.PlatformAuditService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Shared exact-match contact search for platform admin lists (shops / merchants / users).
 * Phone: last-4 digits only, or full 9–10 digits after {@code +66}/{@code 66} → {@code 0}.
 * Email: full address (case-insensitive) or {@code @domain} suffix. Never substring/LIKE on local-part or phone digits.
 */
public final class AdminContactSearch {
    public static final String PHONE_DIGITS_EXPR =
        "REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(%s, ''), '-', ''), ' ', ''), '+', ''), '(', '')";

    private AdminContactSearch() {}

    public static String phoneDigits(String raw) {
        return AdminContactMask.normalizePhoneDigits(raw == null ? "" : raw);
    }

    /**
     * {@code PHONE} / {@code EMAIL} when {@code q} is a full-contact lookup (audited); null for last-4, {@code @domain}, or non-contact.
     */
    public static String searchType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        var q = raw.trim();
        if (q.startsWith("@") && q.length() > 1 && q.indexOf('@', 1) < 0) return null;
        if (q.indexOf('@') > 0) return "EMAIL";
        var digits = phoneDigits(q);
        if (digits.length() == 9 || digits.length() == 10) return "PHONE";
        return null;
    }

    /**
     * Appends {@code OR ...} contact matchers for the given phone/email columns (parameterized).
     * Does not add a leading AND; caller wraps the name + contact group in parentheses.
     */
    public static void appendMatchers(StringBuilder sql, MapSqlParameterSource p, String raw,
                                      List<String> phoneColumns, List<String> emailColumns) {
        if (raw == null || raw.isBlank()) return;
        var q = raw.trim();
        var digits = phoneDigits(q);
        if (digits.length() == 4) {
            p.addValue("qLast4", digits);
            for (var col : phoneColumns) {
                sql.append(" OR RIGHT(").append(PHONE_DIGITS_EXPR.formatted(col)).append(", 4) = :qLast4");
            }
        } else if (digits.length() == 9 || digits.length() == 10) {
            var variants = new ArrayList<String>();
            variants.add(digits);
            if (digits.startsWith("0") && digits.length() >= 9) variants.add("66" + digits.substring(1));
            p.addValue("qPhoneExact", variants);
            for (var col : phoneColumns) {
                sql.append(" OR ").append(PHONE_DIGITS_EXPR.formatted(col)).append(" IN (:qPhoneExact)");
            }
        }
        if (q.startsWith("@") && q.length() > 1 && q.indexOf('@', 1) < 0) {
            p.addValue("qDomain", q.toLowerCase(Locale.ROOT));
            for (var col : emailColumns) {
                sql.append(" OR RIGHT(LOWER(COALESCE(").append(col).append(", '')), LENGTH(:qDomain)) = :qDomain");
            }
        } else if (q.indexOf('@') > 0) {
            p.addValue("qEmailExact", q.toLowerCase(Locale.ROOT));
            for (var col : emailColumns) {
                sql.append(" OR LOWER(COALESCE(").append(col).append(", '')) = :qEmailExact");
            }
        }
    }

    /** Records {@code CONTACT_SEARCHED} for full phone/email lookups; never stores the raw query. */
    public static void auditIfNeeded(PlatformAuditService audit, UUID actorId, String q,
                                     String permission, String resourceType) {
        if (audit == null || actorId == null) return;
        var type = searchType(q);
        if (type == null) return;
        audit.record(PlatformAuditService.Entry.platform(actorId, "CONTACT_SEARCHED", resourceType, null)
            .permission(permission)
            .change(Map.of("searchType", type), Map.of("searchType", type)));
    }
}
