package com.thaishopfun.platform;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.thaishopfun.audit.PlatformAuditService;
import com.thaishopfun.auth.AuthException;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.customer.ContactNormalizer;
import com.thaishopfun.merchant.ShopChannelUrls;
import com.thaishopfun.platform.SupportContactDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SupportContactService {
    public static final String KEY = "support.contact";
    private static final String PERMISSION = "PLATFORM_SETTING_UPDATE";
    private static final Pattern HH_MM = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final PlatformSettingRepository settings;
    private final PlatformAuditService audit;
    private final ObjectMapper mapper;

    public SupportContactService(PlatformSettingRepository settings, PlatformAuditService audit, ObjectMapper mapper) {
        this.settings = settings;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PublicSupportContact publicContact() {
        return toPublic(readValue(requireRow()));
    }

    @Transactional(readOnly = true)
    public SupportContact adminContact() {
        return readValue(requireRow());
    }

    @Transactional
    public SupportContact update(SupportContact request, UUID actorId) {
        var row = requireRow();
        if (!row.isAdminEditable()) {
            throw new AuthException(HttpStatus.FORBIDDEN, "SETTING_NOT_EDITABLE", "การตั้งค่านี้แก้ไขผ่าน migration/ops เท่านั้น");
        }
        var normalized = validateAndNormalize(request);
        var before = row.getSettingValue();
        var after = mapper.convertValue(normalized, MAP);
        row.replaceValue(after, actorId);
        settings.save(row);
        audit.record(PlatformAuditService.Entry.platform(actorId, "PLATFORM_SETTING_UPDATE", "platform_settings", row.getId())
            .permission(PERMISSION)
            .change(Map.of("key", KEY, "value", safeAudit(before)), Map.of("key", KEY, "value", safeAudit(after))));
        return normalized;
    }

    private PlatformSetting requireRow() {
        return settings.findBySettingKey(KEY)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SETTING_NOT_FOUND", "ยังไม่ได้ตั้งค่าข้อมูลติดต่อแพลตฟอร์ม"));
    }

    private SupportContact readValue(PlatformSetting row) {
        try {
            return mapper.convertValue(row.getSettingValue(), SupportContact.class);
        } catch (IllegalArgumentException ex) {
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "SETTING_CORRUPT", "รูปแบบข้อมูลติดต่อไม่ถูกต้อง");
        }
    }

    private PublicSupportContact toPublic(SupportContact value) {
        return new PublicSupportContact(
            value.chat() != null && value.chat().enabled()
                ? new PublicChat(blankToNull(value.chat().url()), value.chat().hours())
                : null,
            value.line() != null && value.line().enabled()
                ? new PublicLine(blankToNull(value.line().id()), blankToNull(value.line().url()))
                : null,
            value.email() != null && value.email().enabled()
                ? new PublicEmail(blankToNull(value.email().address()))
                : null,
            value.phone() != null && value.phone().enabled()
                ? new PublicPhone(blankToNull(value.phone().number()), value.phone().hours())
                : null
        );
    }

    private SupportContact validateAndNormalize(SupportContact request) {
        if (request == null || request.chat() == null || request.line() == null || request.email() == null || request.phone() == null) {
            throw new ValidationException(List.of(new FieldViolation("body", "REQUIRED", "ต้องส่ง chat, line, email และ phone")));
        }
        List<FieldViolation> errors = new ArrayList<>();

        var chatHours = normalizeHours("chat.hours", request.chat().hours(), request.chat().enabled(), errors);
        String chatUrl = blankToNull(request.chat().url());
        if (chatUrl != null) {
            chatUrl = requireHttps("chat.url", chatUrl, errors);
        }

        String lineId = blankToNull(request.line().id());
        String lineUrl = blankToNull(request.line().url());
        if (lineUrl != null) {
            try {
                String normalized = ShopChannelUrls.normalizeOrThrow(null, lineUrl, null, null, null).lineId();
                if (normalized == null || !normalized.toLowerCase(Locale.ROOT).startsWith("https://")) {
                    errors.add(new FieldViolation("line.url", "INVALID", "ลิงก์ LINE ต้องเป็น line.me หรือ lin.ee"));
                    lineUrl = null;
                } else {
                    lineUrl = normalized;
                }
            } catch (AuthException ex) {
                errors.add(new FieldViolation("line.url", ex.getCode() != null ? ex.getCode() : "INVALID", ex.getMessage()));
                lineUrl = null;
            }
        }
        if (lineId != null) {
            try {
                String normalized = ShopChannelUrls.normalizeOrThrow(null, lineId, null, null, null).lineId();
                if (normalized != null && normalized.toLowerCase(Locale.ROOT).startsWith("https://")) {
                    if (lineUrl == null) lineUrl = normalized;
                    lineId = null;
                } else {
                    lineId = normalized;
                }
            } catch (AuthException ex) {
                errors.add(new FieldViolation("line.id", ex.getCode() != null ? ex.getCode() : "INVALID", ex.getMessage()));
                lineId = null;
            }
        }
        if (request.line().enabled() && lineId == null && lineUrl == null) {
            errors.add(new FieldViolation("line.id", "REQUIRED", "เปิดใช้ LINE แล้วต้องมี LINE ID หรือลิงก์"));
        }

        String email = ContactNormalizer.email(request.email().address());
        if (request.email().enabled()) {
            if (email == null) {
                errors.add(new FieldViolation("email.address", "REQUIRED", "กรุณากรอกอีเมล"));
            } else if (!ContactNormalizer.isValidEmail(email)) {
                errors.add(new FieldViolation("email.address", "INVALID", "รูปแบบอีเมลไม่ถูกต้อง"));
            }
        } else if (email != null && !ContactNormalizer.isValidEmail(email)) {
            errors.add(new FieldViolation("email.address", "INVALID", "รูปแบบอีเมลไม่ถูกต้อง"));
        }

        String phoneRaw = blankToNull(request.phone().number());
        String phoneDisplay = phoneRaw;
        if (phoneRaw != null) {
            String normalizedPhone = ContactNormalizer.phone(phoneRaw);
            if (!ContactNormalizer.isValidPhone(normalizedPhone)) {
                errors.add(new FieldViolation("phone.number", "INVALID", "เบอร์โทรไม่ถูกต้อง"));
            }
        }
        if (request.phone().enabled() && phoneRaw == null) {
            errors.add(new FieldViolation("phone.number", "REQUIRED", "กรุณากรอกเบอร์โทร"));
        }
        var phoneHours = normalizeHours("phone.hours", request.phone().hours(), request.phone().enabled(), errors);

        if (!errors.isEmpty()) {
            throw new ValidationException(ValidationException.topCode(errors), errors);
        }

        return new SupportContact(
            new ChatChannel(request.chat().enabled(), chatUrl, chatHours),
            new LineChannel(request.line().enabled(), lineId, lineUrl),
            new EmailChannel(request.email().enabled(), email != null ? email : ""),
            new PhoneChannel(request.phone().enabled(), phoneDisplay != null ? phoneDisplay : "", phoneHours)
        );
    }

    private List<DayHours> normalizeHours(String prefix, List<DayHours> hours, boolean channelEnabled, List<FieldViolation> errors) {
        if (hours == null || hours.size() != 7) {
            errors.add(new FieldViolation(prefix, "REQUIRED", "ต้องมีเวลาทำการครบ 7 วัน"));
            return defaultClosedHours();
        }
        Set<Integer> seen = new HashSet<>();
        DayHours[] byDay = new DayHours[7];
        boolean anyOpen = false;
        for (int i = 0; i < hours.size(); i++) {
            DayHours row = hours.get(i);
            if (row == null) {
                errors.add(new FieldViolation(prefix + "[" + i + "]", "REQUIRED", "ข้อมูลวันไม่ครบ"));
                continue;
            }
            int day = row.day();
            if (day < 0 || day > 6) {
                errors.add(new FieldViolation(prefix + "[" + i + "].day", "INVALID", "วันต้องอยู่ระหว่าง 0–6"));
                continue;
            }
            if (!seen.add(day)) {
                errors.add(new FieldViolation(prefix + "[" + i + "].day", "DUPLICATE", "วันซ้ำกัน"));
                continue;
            }
            String start = blankToNull(row.start());
            String end = blankToNull(row.end());
            if (start == null || !HH_MM.matcher(start).matches()) {
                errors.add(new FieldViolation(prefix + "[" + day + "].start", "INVALID", "รูปแบบเวลาต้องเป็น HH:mm"));
                start = "08:00";
            }
            if (end == null || !HH_MM.matcher(end).matches()) {
                errors.add(new FieldViolation(prefix + "[" + day + "].end", "INVALID", "รูปแบบเวลาต้องเป็น HH:mm"));
                end = "18:00";
            }
            if (row.open() && start.compareTo(end) >= 0) {
                errors.add(new FieldViolation(prefix + "[" + day + "].end", "TIME_RANGE_INVALID", "เวลาปิดต้องอยู่หลังเวลาเปิด"));
            }
            if (row.open()) anyOpen = true;
            byDay[day] = new DayHours(day, row.open(), start, end);
        }
        if (seen.size() != 7) {
            errors.add(new FieldViolation(prefix, "REQUIRED", "ต้องมีเวลาทำการครบ 7 วัน ไม่ซ้ำ"));
        }
        if (channelEnabled && !anyOpen) {
            errors.add(new FieldViolation(prefix, "REQUIRED", "เปิดใช้ช่องทางแล้วต้องมีอย่างน้อย 1 วันที่เปิด"));
        }
        List<DayHours> ordered = new ArrayList<>(7);
        for (int day = 0; day < 7; day++) {
            ordered.add(byDay[day] != null ? byDay[day] : new DayHours(day, false, "09:00", "18:00"));
        }
        return ordered;
    }

    private String requireHttps(String field, String raw, List<FieldViolation> errors) {
        try {
            URI uri = URI.create(raw.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
                errors.add(new FieldViolation(field, "INVALID", "ลิงก์ต้องเป็น https"));
                return null;
            }
            if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
                errors.add(new FieldViolation(field, "INVALID", "ห้ามใส่ชื่อผู้ใช้/รหัสผ่านในลิงก์"));
                return null;
            }
            String lower = raw.toLowerCase(Locale.ROOT);
            if (lower.startsWith("javascript:") || lower.startsWith("data:")) {
                errors.add(new FieldViolation(field, "INVALID", "โปรโตคอลนี้ไม่รองรับ"));
                return null;
            }
            return uri.normalize().toString();
        } catch (IllegalArgumentException ex) {
            errors.add(new FieldViolation(field, "INVALID", "ลิงก์ไม่ถูกต้อง"));
            return null;
        }
    }

    /** Defaults used when input is incomplete (validation still reports the error). */
    public static List<DayHours> defaultChatHours() {
        List<DayHours> days = new ArrayList<>(7);
        for (int day = 0; day < 7; day++) {
            days.add(new DayHours(day, true, "08:00", "22:00"));
        }
        return days;
    }

    public static List<DayHours> defaultPhoneHours() {
        List<DayHours> days = new ArrayList<>(7);
        for (int day = 0; day < 7; day++) {
            boolean open = day < 5;
            days.add(new DayHours(day, open, open ? "08:00" : "09:00", "18:00"));
        }
        return days;
    }

    private static List<DayHours> defaultClosedHours() {
        List<DayHours> days = new ArrayList<>(7);
        for (int day = 0; day < 7; day++) {
            days.add(new DayHours(day, false, "09:00", "18:00"));
        }
        return days;
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Map<String, Object> safeAudit(Map<String, Object> value) {
        if (value == null) return Map.of();
        return new LinkedHashMap<>(value);
    }
}
