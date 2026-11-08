package com.thaishopfun.merchant.notification;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.authz.AuthorizationService;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class MerchantNotificationPreferenceService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Map<String, Object>>> PREF_TYPE = new TypeReference<>() {};

    private final ShopRepository shops;
    private final AuthorizationService authz;
    private final NamedParameterJdbcTemplate jdbc;

    public MerchantNotificationPreferenceService(ShopRepository shops, AuthorizationService authz,
                                                 NamedParameterJdbcTemplate jdbc) {
        this.shops = shops;
        this.authz = authz;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public MerchantNotificationPreferenceDtos.PreferencesResponse get(UUID userId, Long shopNo) {
        var shop = requireMemberShop(userId, shopNo);
        return buildResponse(shop, userId, loadRow(shop.getUuid(), userId));
    }

    @Transactional
    public MerchantNotificationPreferenceDtos.PreferencesResponse save(UUID userId, Long shopNo,
                                                                       MerchantNotificationPreferenceDtos.SaveRequest body) {
        var shop = requireMemberShop(userId, shopNo);
        if (body == null || body.version() == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ต้องระบุ version", "version");
        }
        if (body.categories() == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ต้องระบุ categories", "categories");
        }

        var stored = loadRow(shop.getUuid(), userId);
        if (stored.version() != body.version()) {
            throw new AuthException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                "ค่าการแจ้งเตือนถูกแก้ไขจากแท็บอื่นแล้ว กรุณาโหลดใหม่แล้วบันทึกอีกครั้ง");
        }

        var effective = new LinkedHashMap<>(stored.inAppByCategory());
        for (var patch : body.categories()) {
            if (patch == null || patch.code() == null || patch.code().isBlank()) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "UNKNOWN_CATEGORY",
                    "ไม่รู้จักหมวดการแจ้งเตือน", "categories");
            }
            var cat = MerchantNotificationPreferenceCatalog.byCode(patch.code())
                .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "UNKNOWN_CATEGORY",
                    "ไม่รู้จักหมวดการแจ้งเตือน: " + patch.code(), "categories"));
            if (patch.inAppEnabled() == null) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "ต้องระบุ inAppEnabled", "categories");
            }
            if (cat.mandatory() && !Boolean.TRUE.equals(patch.inAppEnabled())) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "MANDATORY_CATEGORY",
                    "ไม่สามารถปิดหมวดบังคับได้: " + cat.code(), "categories");
            }
            if (!hasPermission(userId, shopNo, cat.viewerPermission())) {
                throw new AuthException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
                    "คุณไม่มีสิทธิ์ตั้งค่าหมวดนี้ (" + cat.viewerPermission() + ")");
            }
            effective.put(cat.code(), Boolean.TRUE.equals(patch.inAppEnabled()));
        }

        for (var cat : MerchantNotificationPreferenceCatalog.all()) {
            if (cat.mandatory()) effective.put(cat.code(), true);
        }

        long nextVersion = stored.exists() ? stored.version() + 1 : 1L;
        persist(shop.getUuid(), userId, effective, stored.exists(), stored.version(), nextVersion);
        return buildResponse(shop, userId, new StoredPrefs(true, nextVersion, effective));
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> effectiveInAppMap(UUID shopUuid, UUID userId) {
        return loadRow(shopUuid, userId).inAppByCategory();
    }

    public Set<String> enabledEventTypes(UUID shopUuid, UUID userId) {
        return MerchantNotificationPreferenceCatalog.enabledEventTypes(effectiveInAppMap(shopUuid, userId));
    }

    private Shop requireMemberShop(UUID userId, Long shopNo) {
        var shop = shops.findByShopId(shopNo)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้านี้"));
        boolean ok = hasPermission(userId, shopNo, "SHOP_READ")
            || hasPermission(userId, shopNo, "ORDER_READ")
            || hasPermission(userId, shopNo, "CUSTOMER_READ")
            || hasPermission(userId, shopNo, "PRODUCT_READ")
            || hasPermission(userId, shopNo, "SHIPMENT_READ")
            || hasPermission(userId, shopNo, "USER_ROLE_UPDATE");
        if (!ok) {
            throw new AuthException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
                "คุณไม่มีสิทธิ์ตั้งค่าการแจ้งเตือนของร้านนี้");
        }
        return shop;
    }

    private boolean hasPermission(UUID userId, Long shopNo, String permission) {
        return authz.checkShop(userId, shopNo, permission) == AuthorizationService.ShopDecision.GRANTED;
    }

    private MerchantNotificationPreferenceDtos.PreferencesResponse buildResponse(Shop shop, UUID userId, StoredPrefs stored) {
        var channels = new LinkedHashMap<String, MerchantNotificationPreferenceDtos.ChannelInfo>();
        channels.put(MerchantNotificationPreferenceCatalog.CHANNEL_IN_APP,
            new MerchantNotificationPreferenceDtos.ChannelInfo(true, true));
        channels.put("email", new MerchantNotificationPreferenceDtos.ChannelInfo(false, false));
        channels.put("sms", new MerchantNotificationPreferenceDtos.ChannelInfo(false, false));
        channels.put("line", new MerchantNotificationPreferenceDtos.ChannelInfo(false, false));

        var categories = new ArrayList<MerchantNotificationPreferenceDtos.CategoryInfo>();
        for (var cat : MerchantNotificationPreferenceCatalog.all()) {
            boolean hasPerm = hasPermission(userId, shop.getId(), cat.viewerPermission());
            boolean enabled = cat.mandatory()
                || stored.inAppByCategory().getOrDefault(cat.code(), cat.defaultInApp());
            categories.add(new MerchantNotificationPreferenceDtos.CategoryInfo(
                cat.code(),
                cat.mandatory(),
                cat.defaultInApp(),
                enabled,
                !cat.mandatory() && hasPerm,
                cat.viewerPermission(),
                cat.eventTypes(),
                cat.policyReason(),
                hasPerm
            ));
        }
        return new MerchantNotificationPreferenceDtos.PreferencesResponse(
            shop.getId(),
            shop.getName(),
            stored.version(),
            Map.copyOf(channels),
            List.copyOf(categories)
        );
    }

    private StoredPrefs loadRow(UUID shopUuid, UUID userId) {
        var params = new MapSqlParameterSource()
            .addValue("shopId", shopUuid)
            .addValue("userId", userId);
        var rows = jdbc.query("""
            SELECT CAST(preferences_json AS VARCHAR), version
              FROM merchant_notification_preferences
             WHERE shop_id = :shopId AND user_id = :userId
            """, params, (rs, i) -> new Object[]{ rs.getString(1), rs.getLong(2) });
        if (rows.isEmpty()) {
            return new StoredPrefs(false, 1L, MerchantNotificationPreferenceCatalog.defaultsInApp());
        }
        var row = rows.get(0);
        return new StoredPrefs(true, (Long) row[1], parsePrefs((String) row[0]));
    }

    private Map<String, Boolean> parsePrefs(String raw) {
        var defaults = new LinkedHashMap<>(MerchantNotificationPreferenceCatalog.defaultsInApp());
        if (raw == null || raw.isBlank() || "{}".equals(raw.trim())) {
            return defaults;
        }
        try {
            Map<String, Map<String, Object>> parsed = JSON.readValue(raw, PREF_TYPE);
            for (var entry : parsed.entrySet()) {
                var cat = MerchantNotificationPreferenceCatalog.byCode(entry.getKey());
                if (cat.isEmpty()) continue;
                Object inApp = entry.getValue() == null ? null : entry.getValue().get("inApp");
                Boolean enabled = asBoolean(inApp);
                if (enabled != null) {
                    defaults.put(cat.get().code(), cat.get().mandatory() || enabled);
                }
            }
            return defaults;
        } catch (RuntimeException ex) {
            return defaults;
        }
    }

    private void persist(UUID shopUuid, UUID userId, Map<String, Boolean> effective,
                         boolean exists, long expectedVersion, long nextVersion) {
        String payload;
        try {
            var body = new LinkedHashMap<String, Map<String, Object>>();
            for (var e : effective.entrySet()) {
                body.put(e.getKey(), Map.of("inApp", e.getValue()));
            }
            payload = JSON.writeValueAsString(body);
        } catch (RuntimeException ex) {
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "บันทึกการตั้งค่าไม่สำเร็จ");
        }
        var params = new MapSqlParameterSource()
            .addValue("shopId", shopUuid)
            .addValue("userId", userId)
            .addValue("json", payload)
            .addValue("version", nextVersion)
            .addValue("expected", expectedVersion)
            .addValue("updatedAt", Timestamp.from(Instant.now()));
        if (exists) {
            int updated = jdbc.update("""
                UPDATE merchant_notification_preferences
                   SET preferences_json = :json,
                       version = :version,
                       updated_at = :updatedAt
                 WHERE shop_id = :shopId AND user_id = :userId AND version = :expected
                """, params);
            if (updated == 0) {
                throw new AuthException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "ค่าการแจ้งเตือนถูกแก้ไขจากแท็บอื่นแล้ว กรุณาโหลดใหม่แล้วบันทึกอีกครั้ง");
            }
            return;
        }
        try {
            jdbc.update("""
                INSERT INTO merchant_notification_preferences (shop_id, user_id, preferences_json, version, updated_at)
                VALUES (:shopId, :userId, :json, :version, :updatedAt)
                """, params);
        } catch (DuplicateKeyException dup) {
            throw new AuthException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                "ค่าการแจ้งเตือนถูกแก้ไขจากแท็บอื่นแล้ว กรุณาโหลดใหม่แล้วบันทึกอีกครั้ง");
        }
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) {
            if ("true".equalsIgnoreCase(s)) return true;
            if ("false".equalsIgnoreCase(s)) return false;
        }
        return null;
    }

    private record StoredPrefs(boolean exists, long version, Map<String, Boolean> inAppByCategory) {}
}
