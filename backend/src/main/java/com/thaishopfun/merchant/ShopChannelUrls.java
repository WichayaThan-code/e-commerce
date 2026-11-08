package com.thaishopfun.merchant;

import com.thaishopfun.auth.AuthException;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalize / validate shop contact channels (website, LINE, Facebook, Instagram, TikTok).
 * Values are contact links only — not OAuth connections or API sync state.
 */
public final class ShopChannelUrls {
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}]");
    private static final Pattern FACEBOOK = Pattern.compile("(?i)^([\\w-]+\\.)*(facebook|fb)\\.com$");
    private static final Pattern INSTAGRAM = Pattern.compile("(?i)^([\\w-]+\\.)*instagram\\.com$");
    private static final Pattern TIKTOK = Pattern.compile("(?i)^([\\w-]+\\.)*tiktok\\.com$");
    private static final Pattern LINE_HOST = Pattern.compile("(?i)^(line\\.me|lin\\.ee)$");
    private static final Pattern LINE_ID = Pattern.compile("^@?[A-Za-z0-9._-]{2,50}$");
    private static final Pattern HOST_LIKE = Pattern.compile("(?i)^[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/.*)?$");

    private ShopChannelUrls() {}

    public record Channels(String website, String lineId, String facebookUrl, String instagramUrl, String tiktokUrl) {}

    public static Channels normalizeOrThrow(String website, String lineId, String facebookUrl, String instagramUrl, String tiktokUrl) {
        return new Channels(
            normalizeWebsite(website),
            normalizeLine(lineId),
            normalizeNetwork("facebookUrl", facebookUrl, FACEBOOK, "https://www.facebook.com/"),
            normalizeNetwork("instagramUrl", instagramUrl, INSTAGRAM, "https://www.instagram.com/"),
            normalizeNetwork("tiktokUrl", tiktokUrl, TIKTOK, "https://www.tiktok.com/@")
        );
    }

    private static String normalizeWebsite(String raw) {
        String value = blankToNull(raw);
        if (value == null) return null;
        rejectControl(value, "website");
        try {
            URI uri = URI.create(hasScheme(value) ? value : "https://" + value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw field("website", "รองรับเฉพาะลิงก์ http/https");
            }
            if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
                throw field("website", "ห้ามใส่ชื่อผู้ใช้/รหัสผ่านในลิงก์");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw field("website", "ลิงก์เว็บไซต์ไม่ถูกต้อง");
            }
            return uri.normalize().toString();
        } catch (IllegalArgumentException | AuthException ex) {
            if (ex instanceof AuthException auth) throw auth;
            throw field("website", "ลิงก์เว็บไซต์ไม่ถูกต้อง");
        }
    }

    private static String normalizeLine(String raw) {
        String value = blankToNull(raw);
        if (value == null) return null;
        rejectControl(value, "lineId");
        String compact = value.replaceAll("\\s+", "");
        if (compact.toLowerCase(Locale.ROOT).startsWith("https://")) {
            try {
                URI uri = URI.create(compact);
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || !LINE_HOST.matcher(host).matches()) {
                    throw field("lineId", "ลิงก์ LINE ต้องเป็น line.me หรือ lin.ee");
                }
                if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
                    throw field("lineId", "ห้ามใส่ชื่อผู้ใช้/รหัสผ่านในลิงก์");
                }
                return uri.normalize().toString();
            } catch (IllegalArgumentException ex) {
                throw field("lineId", "ลิงก์ LINE ไม่ถูกต้อง");
            }
        }
        if (hasScheme(compact) || compact.contains("/")) {
            throw field("lineId", "ใส่ LINE ID หรือลิงก์ https://line.me/...");
        }
        if (!LINE_ID.matcher(compact).matches()) {
            throw field("lineId", "รูปแบบ LINE ID ไม่ถูกต้อง");
        }
        return compact;
    }

    private static String normalizeNetwork(String field, String raw, Pattern hostPattern, String base) {
        String value = blankToNull(raw);
        if (value == null) return null;
        rejectControl(value, field);
        if (hasScheme(value) || HOST_LIKE.matcher(value).matches()) {
            try {
                URI uri = URI.create(hasScheme(value) ? value : "https://" + value);
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                if (!scheme.equals("http") && !scheme.equals("https")) {
                    throw field(field, "รองรับเฉพาะลิงก์ http/https");
                }
                if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
                    throw field(field, "ห้ามใส่ชื่อผู้ใช้/รหัสผ่านในลิงก์");
                }
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
                if (!hostPattern.matcher(host).matches()) {
                    throw field(field, "โดเมนไม่ตรงกับแพลตฟอร์ม");
                }
                String path = uri.getRawPath() == null ? "" : uri.getRawPath();
                String query = uri.getRawQuery() == null ? "" : ("?" + uri.getRawQuery());
                return "https://" + host + path + query;
            } catch (IllegalArgumentException | AuthException ex) {
                if (ex instanceof AuthException auth) throw auth;
                throw field(field, "ลิงก์ไม่ถูกต้อง");
            }
        }
        String handle = value.replaceFirst("^@", "");
        if (handle.isBlank() || hasScheme(handle) || handle.contains("/") || handle.contains("\\") || handle.contains(" ")) {
            throw field(field, "ลิงก์หรือชื่อบัญชีไม่ถูกต้อง");
        }
        return base + handle;
    }

    private static boolean hasScheme(String value) {
        return Pattern.compile("(?i)^[a-z][a-z0-9+.-]*:").matcher(value).find();
    }

    private static void rejectControl(String value, String field) {
        if (CONTROL.matcher(value).find()) throw field(field, "มีอักขระที่ไม่รองรับ");
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("data:")) {
            throw field(field, "โปรโตคอลนี้ไม่รองรับ");
        }
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static AuthException field(String field, String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_CHANNEL", message, field);
    }
}
