package com.thaishopfun.admin;

import com.thaishopfun.admin.AdminPlatformDtos.Page;
import com.thaishopfun.admin.AdminPlatformDtos.UserRef;
import com.thaishopfun.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Small shared helpers of the platform admin read APIs (user names, paging, shop status mapping). */
@Component
public class AdminRefs {
    static final int MAX_PAGE_SIZE = AdminUserDirectoryService.MAX_PAGE_SIZE;
    /** API shop statuses (as in the UI) and the stored shops.status values they stand for. */
    static final Map<String, List<String>> SHOP_STATUS_DB = Map.of(
        "PENDING_REVIEW", List.of("PENDING_REVIEW"),
        "APPROVED", List.of("ACTIVE"),
        "REJECTED", List.of("REJECTED", "CLOSED"),
        "SUSPENDED", List.of("SUSPENDED"));

    private final NamedParameterJdbcTemplate jdbc;

    public AdminRefs(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<UUID, UserRef> users(Collection<UUID> ids) {
        var map = new HashMap<UUID, UserRef>();
        var wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (wanted.isEmpty()) return map;
        jdbc.query("SELECT id, first_name, last_name, email, phone, status FROM users WHERE id IN (:ids)",
            new MapSqlParameterSource("ids", wanted), rs -> {
                var id = rs.getObject("id", UUID.class);
                map.put(id, new UserRef(id, rs.getString("first_name"), rs.getString("last_name"), rs.getString("email"),
                    rs.getString("phone"), rs.getString("status")));
            });
        return map;
    }

    static String name(UserRef user) {
        if (user == null) return null;
        var full = (Objects.toString(user.firstName(), "") + " " + Objects.toString(user.lastName(), "")).trim();
        return full.isEmpty() ? user.email() : full;
    }

    static String apiShopStatus(String stored) {
        if (stored == null) return null;
        return switch (stored) {
            case "ACTIVE" -> "APPROVED";
            case "CLOSED" -> "REJECTED";
            default -> stored;
        };
    }

    static List<String> storedShopStatuses(String apiStatus) {
        var values = SHOP_STATUS_DB.get(apiStatus.trim().toUpperCase(Locale.ROOT));
        if (values == null) throw invalid("status", "สถานะร้านต้องเป็น " + String.join(", ", new TreeSet<>(SHOP_STATUS_DB.keySet())));
        return values;
    }

    static int size(int size) { return Math.min(Math.max(size, 1), MAX_PAGE_SIZE); }

    static int page(int page) { return Math.max(page, 0); }

    static <T> Page<T> page(List<T> items, int page, int size, long total) {
        return new Page<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    static String like(String q) {
        return "%" + q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    static Timestamp startOf(LocalDate date) {
        return Timestamp.from(date.atStartOfDay(AdminUserDirectoryService.ZONE).toInstant());
    }

    static Timestamp endOf(LocalDate date) {
        return Timestamp.from(date.plusDays(1).atStartOfDay(AdminUserDirectoryService.ZONE).toInstant());
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    static String direction(String dir) {
        return "asc".equalsIgnoreCase(dir) ? "ASC" : "DESC";
    }

    static AuthException invalid(String field, String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", field + ": " + message);
    }
}
