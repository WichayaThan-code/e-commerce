package com.thaishopfun.admin;

import com.thaishopfun.admin.AdminPlatformDtos.*;
import com.thaishopfun.audit.PlatformAuditLog;
import com.thaishopfun.audit.PlatformAuditLogRepository;
import com.thaishopfun.auth.AuthException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Admin query API over V1 audit_logs (business rows from the admin services + HTTP rows from UserActionAudit).
 * Sensitive keys inside before / after (phone, email, address, citizen id, bank, token, password, secret ...) are
 * masked before they leave the server, matching PLATFORM_AUDIT_READ ("ตัดข้อมูลอ่อนไหวแล้ว").
 */
@Service
public class AdminAuditLogService {
    private static final Pattern SENSITIVE = Pattern.compile("(?i).*(phone|email|address|citizen|bank|account_?number|token|password|secret|otp|tax|birth).*");
    static final Set<String> KINDS = Set.of("ALL", "BUSINESS", "HTTP");

    private final PlatformAuditLogRepository logs;
    private final NamedParameterJdbcTemplate jdbc;
    private final AdminRefs refs;

    public AdminAuditLogService(PlatformAuditLogRepository logs, NamedParameterJdbcTemplate jdbc, AdminRefs refs) {
        this.logs = logs;
        this.jdbc = jdbc;
        this.refs = refs;
    }

    public record Filter(UUID actorId, String action, String resourceType, UUID resourceId, Long shopId, String outcome,
                         String authContext, String reasonCode, String kind, String q, LocalDate from, LocalDate to) {}

    @Transactional(readOnly = true)
    public Page<AuditLogView> list(Filter filter, String dir, int page, int size) {
        var safeSize = AdminRefs.size(size);
        var safePage = AdminRefs.page(page);
        var spec = spec(filter);
        var sort = "asc".equalsIgnoreCase(dir) ? Sort.by("occurredAt").ascending().and(Sort.by("id").ascending())
            : Sort.by("occurredAt").descending().and(Sort.by("id").descending());
        var result = logs.findAll(spec, PageRequest.of(safePage, safeSize, sort));
        return AdminRefs.page(views(result.getContent()), safePage, safeSize, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AuditLogView detail(Long id) {
        var log = logs.findById(id).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "AUDIT_LOG_NOT_FOUND", "ไม่พบรายการ Audit"));
        return views(List.of(log)).get(0);
    }

    private Specification<PlatformAuditLog> spec(Filter f) {
        UUID shopUuid = null;
        if (f.shopId() != null) {
            var found = jdbc.queryForList("SELECT id FROM shops WHERE shop_no = :no", new MapSqlParameterSource("no", f.shopId()), UUID.class);
            shopUuid = found.isEmpty() ? new UUID(0, 0) : found.get(0);
        }
        var kind = f.kind() == null || f.kind().isBlank() ? "ALL" : f.kind().trim().toUpperCase(Locale.ROOT);
        if (!KINDS.contains(kind)) throw AdminRefs.invalid("kind", "ต้องเป็น ALL, BUSINESS หรือ HTTP");
        var shop = shopUuid;
        return (root, query, cb) -> {
            var all = new ArrayList<Predicate>();
            if (f.actorId() != null) all.add(cb.equal(root.get("actorId"), f.actorId()));
            if (notBlank(f.action())) {
                var action = f.action().trim();
                all.add(action.endsWith("*") ? cb.like(root.get("action"), action.substring(0, action.length() - 1) + "%") : cb.equal(root.get("action"), action));
            }
            if (notBlank(f.resourceType())) all.add(cb.equal(root.get("resourceType"), f.resourceType().trim().toUpperCase(Locale.ROOT)));
            if (f.resourceId() != null) all.add(cb.equal(root.get("resourceId"), f.resourceId()));
            if (shop != null) all.add(cb.or(cb.equal(root.get("shopId"), shop), cb.and(cb.equal(root.get("resourceType"), "SHOP"), cb.equal(root.get("resourceId"), shop))));
            if (notBlank(f.outcome())) all.add(cb.equal(root.get("outcome"), f.outcome().trim().toUpperCase(Locale.ROOT)));
            if (notBlank(f.authContext())) all.add(cb.equal(root.get("authContext"), f.authContext().trim().toUpperCase(Locale.ROOT)));
            if (notBlank(f.reasonCode())) all.add(cb.equal(root.get("reasonCode"), f.reasonCode().trim()));
            if ("HTTP".equals(kind)) all.add(cb.like(root.get("reasonCode"), "HTTP\\_%", '\\'));
            if ("BUSINESS".equals(kind)) all.add(cb.or(cb.isNull(root.get("reasonCode")), cb.notLike(root.get("reasonCode"), "HTTP\\_%", '\\')));
            if (notBlank(f.q())) {
                var q = AdminRefs.like(f.q());
                all.add(cb.or(cb.like(cb.lower(root.get("action")), q, '\\'), cb.like(cb.lower(cb.coalesce(root.get("reason"), "")), q, '\\'),
                    cb.like(cb.lower(cb.coalesce(root.get("reasonCode"), "")), q, '\\')));
            }
            if (f.from() != null) all.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), AdminRefs.startOf(f.from()).toInstant()));
            if (f.to() != null) all.add(cb.lessThan(root.get("occurredAt"), AdminRefs.endOf(f.to()).toInstant()));
            return cb.and(all.toArray(Predicate[]::new));
        };
    }

    private List<AuditLogView> views(List<PlatformAuditLog> rows) {
        var users = refs.users(rows.stream().map(PlatformAuditLog::getActorId).filter(Objects::nonNull).toList());
        var shopIds = new HashSet<UUID>();
        for (var row : rows) {
            if (row.getShopId() != null) shopIds.add(row.getShopId());
            if ("SHOP".equals(row.getResourceType()) && row.getResourceId() != null) shopIds.add(row.getResourceId());
        }
        var shops = new HashMap<UUID, Object[]>();
        if (!shopIds.isEmpty()) {
            jdbc.query("SELECT id, shop_no, name FROM shops WHERE id IN (:ids)", new MapSqlParameterSource("ids", shopIds),
                rs -> { shops.put(rs.getObject("id", UUID.class), new Object[]{rs.getLong("shop_no"), rs.getString("name")}); });
        }
        var out = new ArrayList<AuditLogView>();
        for (var row : rows) {
            var user = row.getActorId() == null ? null : users.get(row.getActorId());
            var shopKey = row.getShopId() != null ? row.getShopId() : "SHOP".equals(row.getResourceType()) ? row.getResourceId() : null;
            var shop = shopKey == null ? null : shops.get(shopKey);
            var http = row.getReasonCode() != null && row.getReasonCode().startsWith("HTTP_");
            out.add(new AuditLogView(row.getId(), row.getOccurredAt(),
                new AuditActor(row.getActorId(), row.getActorType(), AdminRefs.name(user), user == null ? null : user.email()),
                row.getAction(),
                new AuditTarget(row.getResourceType(), row.getResourceId(), shop == null ? null : (Long) shop[0], shop == null ? null : (String) shop[1]),
                row.getPermission(), row.getOutcome(), row.getReasonCode(), row.getReason(),
                redact(row.getBeforeData()), redact(row.getAfterData()), row.getIpAddress(), row.getUserAgent(), row.getRequestId(),
                row.getAuthContext(), row.getPolicyVersion(), http ? "HTTP" : "BUSINESS"));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> redact(Map<String, Object> data) {
        if (data == null) return null;
        var copy = new LinkedHashMap<String, Object>();
        data.forEach((key, value) -> {
            if (key != null && SENSITIVE.matcher(key).matches() && value != null) copy.put(key, "***");
            else if (value instanceof Map<?, ?> nested) copy.put(key, redact((Map<String, Object>) nested));
            else copy.put(key, value);
        });
        return copy;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
