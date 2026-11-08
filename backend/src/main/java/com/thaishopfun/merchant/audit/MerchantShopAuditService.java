package com.thaishopfun.merchant.audit;

import com.thaishopfun.admin.AdminAuditLogService;
import com.thaishopfun.audit.PlatformAuditLog;
import com.thaishopfun.audit.PlatformAuditLogRepository;
import com.thaishopfun.auth.AuthException;
import com.thaishopfun.merchant.Shop;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Shop-scoped read API over {@code audit_logs} for merchants with {@code AUDIT_READ}.
 * Only allowlisted business actions that already carry {@code shop_id} (or SHOP resource) are exposed.
 */
@Service
public class MerchantShopAuditService {
    public static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");

    /** Actions with real writers today that are safe to surface on the security tab. */
    public static final Set<String> SUPPORTED_ACTIONS = Set.of(
        "SHOP_MEMBER_ROLE_ASSIGNED",
        "SHOP_MEMBER_REMOVED",
        "SHOP_SUSPENDED",
        "SHOP_REACTIVATED",
        "SHOP_PLAN_CHANGED"
    );

    private static final Set<String> DETAIL_KEYS = Set.of(
        "status", "planCode", "shopId", "targetName", "suspendReasonCode"
    );

    private static final Set<String> OUTCOMES = Set.of("SUCCESS", "FAILURE", "DENIED");

    private final PlatformAuditLogRepository logs;
    private final NamedParameterJdbcTemplate jdbc;

    public MerchantShopAuditService(PlatformAuditLogRepository logs, NamedParameterJdbcTemplate jdbc) {
        this.logs = logs;
        this.jdbc = jdbc;
    }

    public record Filter(String action, String outcome, LocalDate from, LocalDate to) {}

    @Transactional(readOnly = true)
    public MerchantShopAuditDtos.Page list(Shop shop, Filter filter, int page, int size) {
        var safeSize = Math.min(Math.max(size, 1), 50);
        var safePage = Math.max(page, 0);
        var action = normalizeAction(filter.action());
        var outcome = normalizeOutcome(filter.outcome());
        var spec = spec(shop.getUuid(), action, outcome, filter.from(), filter.to());
        var sort = Sort.by("occurredAt").descending().and(Sort.by("id").descending());
        var result = logs.findAll(spec, PageRequest.of(safePage, safeSize, sort));
        var rows = views(result.getContent());
        var totalPages = result.getTotalElements() == 0 ? 0 : (int) Math.ceil(result.getTotalElements() / (double) safeSize);
        return new MerchantShopAuditDtos.Page(
            rows.stream().map(this::toRow).toList(),
            safePage,
            safeSize,
            result.getTotalElements(),
            totalPages,
            supportedActionsList()
        );
    }

    @Transactional(readOnly = true)
    public MerchantShopAuditDtos.ActivityDetail detail(Shop shop, long id) {
        var log = logs.findById(id)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "AUDIT_LOG_NOT_FOUND", "ไม่พบรายการกิจกรรม"));
        if (!belongsToShop(log, shop.getUuid()) || !SUPPORTED_ACTIONS.contains(log.getAction())) {
            throw new AuthException(HttpStatus.NOT_FOUND, "AUDIT_LOG_NOT_FOUND", "ไม่พบรายการกิจกรรม");
        }
        return views(List.of(log)).get(0);
    }

    private Specification<PlatformAuditLog> spec(UUID shopUuid, String action, String outcome, LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            var all = new ArrayList<Predicate>();
            all.add(cb.or(
                cb.equal(root.get("shopId"), shopUuid),
                cb.and(cb.equal(root.get("resourceType"), "SHOP"), cb.equal(root.get("resourceId"), shopUuid))
            ));
            if (action != null) {
                all.add(cb.equal(root.get("action"), action));
            } else {
                all.add(root.get("action").in(SUPPORTED_ACTIONS));
            }
            // Exclude raw HTTP audit rows even if action name somehow matches.
            all.add(cb.or(cb.isNull(root.get("reasonCode")), cb.notLike(root.get("reasonCode"), "HTTP\\_%", '\\')));
            if (outcome != null) all.add(cb.equal(root.get("outcome"), outcome));
            if (from != null) {
                all.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from.atStartOfDay(BANGKOK).toInstant()));
            }
            if (to != null) {
                all.add(cb.lessThan(root.get("occurredAt"), to.plusDays(1).atStartOfDay(BANGKOK).toInstant()));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }

    private List<MerchantShopAuditDtos.ActivityDetail> views(List<PlatformAuditLog> rows) {
        var actorIds = rows.stream().map(PlatformAuditLog::getActorId).filter(Objects::nonNull).distinct().toList();
        var names = new LinkedHashMap<UUID, String>();
        if (!actorIds.isEmpty()) {
            jdbc.query(
                """
                SELECT id, first_name, last_name FROM users WHERE id IN (:ids)
                """,
                new MapSqlParameterSource("ids", actorIds),
                rs -> {
                    var full = (Objects.toString(rs.getString("first_name"), "") + " "
                        + Objects.toString(rs.getString("last_name"), "")).trim().replaceAll(" +", " ");
                    if (!full.isEmpty()) names.put(rs.getObject("id", UUID.class), full);
                }
            );
        }
        var out = new ArrayList<MerchantShopAuditDtos.ActivityDetail>();
        for (var row : rows) {
            var actorName = row.getActorId() == null ? null : names.get(row.getActorId());
            out.add(new MerchantShopAuditDtos.ActivityDetail(
                row.getId(),
                row.getOccurredAt(),
                actorName,
                row.getActorType(),
                row.getAction(),
                row.getOutcome(),
                summary(row),
                row.getReasonCode(),
                allowlisted(AdminAuditLogService.redact(row.getBeforeData())),
                allowlisted(AdminAuditLogService.redact(row.getAfterData()))
            ));
        }
        return out;
    }

    private MerchantShopAuditDtos.ActivityRow toRow(MerchantShopAuditDtos.ActivityDetail detail) {
        return new MerchantShopAuditDtos.ActivityRow(
            detail.id(),
            detail.occurredAt(),
            detail.actorName(),
            detail.actorType(),
            detail.action(),
            detail.outcome(),
            detail.summary(),
            detail.reasonCode()
        );
    }

    private static String summary(PlatformAuditLog row) {
        if (row.getReason() != null && !row.getReason().isBlank()) return row.getReason().trim();
        // Team writes put the human-readable note in reasonCode (e.g. STAFF->MANAGER).
        if (row.getReasonCode() != null && !row.getReasonCode().isBlank()
            && !row.getReasonCode().startsWith("HTTP_")
            && !row.getReasonCode().startsWith("ADMIN_")
            && !row.getReasonCode().startsWith("PLATFORM_")) {
            return row.getReasonCode().trim();
        }
        if (row.getAfterData() != null) {
            var target = row.getAfterData().get("targetName");
            if (target instanceof String text && !text.isBlank()) return text.trim();
            var plan = row.getAfterData().get("planCode");
            if (plan instanceof String code && !code.isBlank()) return code.trim();
            var status = row.getAfterData().get("status");
            if (status instanceof String st && !st.isBlank()) return st.trim();
        }
        return null;
    }

    private static Map<String, Object> allowlisted(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return null;
        var copy = new LinkedHashMap<String, Object>();
        data.forEach((key, value) -> {
            if (key != null && DETAIL_KEYS.contains(key) && value != null) copy.put(key, value);
        });
        return copy.isEmpty() ? null : copy;
    }

    private static boolean belongsToShop(PlatformAuditLog log, UUID shopUuid) {
        if (shopUuid.equals(log.getShopId())) return true;
        return "SHOP".equals(log.getResourceType()) && shopUuid.equals(log.getResourceId());
    }

    private static String normalizeAction(String action) {
        if (action == null || action.isBlank()) return null;
        var value = action.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_ACTIONS.contains(value)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_ACTION", "ประเภทกิจกรรมนี้ยังไม่มีข้อมูลรองรับ");
        }
        return value;
    }

    private static String normalizeOutcome(String outcome) {
        if (outcome == null || outcome.isBlank()) return null;
        var value = outcome.trim().toUpperCase(Locale.ROOT);
        if (!OUTCOMES.contains(value)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_OUTCOME", "ผลลัพธ์ไม่ถูกต้อง");
        }
        return value;
    }

    private static List<String> supportedActionsList() {
        return List.of(
            "SHOP_MEMBER_ROLE_ASSIGNED",
            "SHOP_MEMBER_REMOVED",
            "SHOP_SUSPENDED",
            "SHOP_REACTIVATED",
            "SHOP_PLAN_CHANGED"
        );
    }
}
