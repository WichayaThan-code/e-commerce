package com.thaishopfun.auth.delivery;

import com.thaishopfun.auth.OtpPurpose;
import com.thaishopfun.common.notification.EmailMessage;
import com.thaishopfun.common.notification.NotificationProperties;
import com.thaishopfun.common.notification.SmsMessage;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Thai e-mail / SMS texts for OTP and password reset (subject + plain text + simple HTML). */
@Component
public class AuthMessageTemplates {
    static final String BRAND = "ThaiShopFun";

    private final NotificationProperties properties;

    public AuthMessageTemplates(NotificationProperties properties) {
        this.properties = properties;
    }

    public EmailMessage otpEmail(String to, String name, OtpPurpose purpose, String code, long ttlMinutes) {
        return otpEmail(to, name, purpose, code, ttlMinutes, null);
    }

    public EmailMessage otpEmail(String to, String name, OtpPurpose purpose, String code, long ttlMinutes, String verifyLinkUrl) {
        var action = switch (purpose) {
            case EMAIL_VERIFY -> "ยืนยันอีเมล";
            case SIGNUP -> "สมัครสมาชิก";
            case PASSWORD_RESET -> "รีเซ็ตรหัสผ่าน";
            case PHONE_VERIFY -> "ยืนยันเบอร์โทรศัพท์";
            default -> "ยืนยันตัวตน";
        };
        var subject = "[" + BRAND + "] รหัส OTP สำหรับ" + action;
        var greeting = greeting(name);
        var hasLink = purpose == OtpPurpose.EMAIL_VERIFY && verifyLinkUrl != null && !verifyLinkUrl.isBlank();
        var text = hasLink
            ? """
            %s

            กดลิงก์ด้านล่างเพื่อยืนยันอีเมลของคุณ (ใช้ได้ครั้งเดียวและหมดอายุภายใน %d นาที):
            %s

            หรือกรอกรหัส OTP: %s

            กรุณาอย่าเปิดเผยรหัสนี้กับผู้อื่น เจ้าหน้าที่ %s จะไม่ขอรหัส OTP จากคุณ
            หากคุณไม่ได้ทำรายการนี้ กรุณาเพิกเฉยต่ออีเมลฉบับนี้

            ทีมงาน %s
            """.formatted(greeting, ttlMinutes, verifyLinkUrl, code, BRAND, BRAND)
            : """
            %s

            รหัส OTP สำหรับ%sของคุณคือ: %s

            รหัสนี้ใช้ได้ครั้งเดียวและจะหมดอายุภายใน %d นาที
            กรุณาอย่าเปิดเผยรหัสนี้กับผู้อื่น เจ้าหน้าที่ %s จะไม่ขอรหัส OTP จากคุณ
            หากคุณไม่ได้ทำรายการนี้ กรุณาเพิกเฉยต่ออีเมลฉบับนี้

            ทีมงาน %s
            """.formatted(greeting, action, code, ttlMinutes, BRAND, BRAND);
        var htmlBody = hasLink
            ? """
            <p style="margin:0 0 16px">%s</p>
            <p style="margin:0 0 16px">กดปุ่มด้านล่างเพื่อยืนยันอีเมลของคุณ</p>
            <p style="margin:0 0 20px"><a href="%s" style="display:inline-block;padding:12px 24px;background:#1268d7;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:600">ยืนยันอีเมล</a></p>
            <p style="margin:0 0 8px">ลิงก์ใช้ได้ครั้งเดียวและจะหมดอายุภายใน <strong>%d นาที</strong></p>
            <p style="margin:0 0 8px">หรือกรอกรหัส OTP:</p>
            <p style="margin:0 0 16px;font-size:32px;font-weight:700;letter-spacing:8px;color:#1268d7">%s</p>
            <p style="margin:0 0 8px;color:#666">กรุณาอย่าเปิดเผยรหัสนี้กับผู้อื่น เจ้าหน้าที่ %s จะไม่ขอรหัส OTP จากคุณ</p>
            <p style="margin:0;color:#666">หากคุณไม่ได้ทำรายการนี้ กรุณาเพิกเฉยต่ออีเมลฉบับนี้</p>
            """.formatted(escape(greeting), escape(verifyLinkUrl), ttlMinutes, escape(code), BRAND)
            : """
            <p style="margin:0 0 16px">%s</p>
            <p style="margin:0 0 8px">รหัส OTP สำหรับ%sของคุณคือ</p>
            <p style="margin:0 0 16px;font-size:32px;font-weight:700;letter-spacing:8px;color:#1268d7">%s</p>
            <p style="margin:0 0 8px">รหัสนี้ใช้ได้ครั้งเดียวและจะหมดอายุภายใน <strong>%d นาที</strong></p>
            <p style="margin:0 0 8px;color:#666">กรุณาอย่าเปิดเผยรหัสนี้กับผู้อื่น เจ้าหน้าที่ %s จะไม่ขอรหัส OTP จากคุณ</p>
            <p style="margin:0;color:#666">หากคุณไม่ได้ทำรายการนี้ กรุณาเพิกเฉยต่ออีเมลฉบับนี้</p>
            """.formatted(escape(greeting), escape(action), escape(code), ttlMinutes, BRAND);
        return new EmailMessage(to, name, subject, text, layout(subject, htmlBody));
    }

    public SmsMessage otpSms(String to, OtpPurpose purpose, String code, long ttlMinutes) {
        return new SmsMessage(to, "%s: รหัส OTP ของคุณคือ %s (หมดอายุใน %d นาที) ห้ามบอกรหัสนี้กับผู้อื่น"
            .formatted(BRAND, code, ttlMinutes));
    }

    public String resetUrl(String token) {
        return properties.frontendBaseUrl() + properties.resetPasswordPathOrDefault()
            + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    public EmailMessage passwordResetEmail(String to, String name, String token, long ttlMinutes) {
        var url = resetUrl(token);
        var subject = "[" + BRAND + "] ลิงก์รีเซ็ตรหัสผ่านของคุณ";
        var greeting = greeting(name);
        var text = """
            %s

            เราได้รับคำขอรีเซ็ตรหัสผ่านสำหรับบัญชี %s ของคุณ
            กดลิงก์ด้านล่างเพื่อตั้งรหัสผ่านใหม่ (ลิงก์ใช้ได้ครั้งเดียวและหมดอายุภายใน %d นาที):

            %s

            หากกดลิงก์ไม่ได้ ให้คัดลอกโทเคนนี้ไปกรอกในหน้ารีเซ็ตรหัสผ่าน:
            %s

            หากคุณไม่ได้ขอรีเซ็ตรหัสผ่าน กรุณาเพิกเฉยต่ออีเมลฉบับนี้ รหัสผ่านเดิมของคุณยังใช้งานได้ตามปกติ

            ทีมงาน %s
            """.formatted(greeting, BRAND, ttlMinutes, url, token, BRAND);
        var html = layout(subject, """
            <p style="margin:0 0 16px">%s</p>
            <p style="margin:0 0 16px">เราได้รับคำขอรีเซ็ตรหัสผ่านสำหรับบัญชี %s ของคุณ กดปุ่มด้านล่างเพื่อตั้งรหัสผ่านใหม่</p>
            <p style="margin:0 0 20px"><a href="%s" style="display:inline-block;padding:12px 24px;background:#1268d7;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:600">ตั้งรหัสผ่านใหม่</a></p>
            <p style="margin:0 0 8px">ลิงก์ใช้ได้ครั้งเดียวและจะหมดอายุภายใน <strong>%d นาที</strong></p>
            <p style="margin:0 0 4px;color:#666">หากกดปุ่มไม่ได้ ให้คัดลอกลิงก์นี้ไปเปิดในเบราว์เซอร์:</p>
            <p style="margin:0 0 12px;word-break:break-all"><a href="%s" style="color:#1268d7">%s</a></p>
            <p style="margin:0 0 4px;color:#666">หรือกรอกโทเคนนี้ในหน้ารีเซ็ตรหัสผ่าน:</p>
            <p style="margin:0 0 16px;font-family:monospace;word-break:break-all">%s</p>
            <p style="margin:0;color:#666">หากคุณไม่ได้ขอรีเซ็ตรหัสผ่าน กรุณาเพิกเฉยต่ออีเมลฉบับนี้ รหัสผ่านเดิมของคุณยังใช้งานได้ตามปกติ</p>
            """.formatted(escape(greeting), BRAND, escape(url), ttlMinutes, escape(url), escape(url), escape(token)));
        return new EmailMessage(to, name, subject, text, html);
    }

    private static String greeting(String name) {
        return name == null || name.isBlank() ? "สวัสดีค่ะ" : "สวัสดีค่ะ คุณ" + name.trim();
    }

    private static String layout(String title, String content) {
        return """
            <!DOCTYPE html>
            <html lang="th"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>%s</title></head>
            <body style="margin:0;padding:24px;background:#f4f6fb;font-family:Tahoma,'Noto Sans Thai',Arial,sans-serif;font-size:15px;line-height:1.6;color:#222">
            <div style="max-width:520px;margin:0 auto;background:#ffffff;border-radius:12px;padding:28px">
            <h1 style="margin:0 0 20px;font-size:20px;color:#1268d7">%s</h1>
            %s
            <hr style="border:0;border-top:1px solid #eee;margin:24px 0 12px">
            <p style="margin:0;font-size:12px;color:#999">อีเมลนี้ส่งอัตโนมัติจาก %s กรุณาอย่าตอบกลับ</p>
            </div>
            </body></html>
            """.formatted(escape(title), BRAND, content, BRAND);
    }

    static String escape(String value) {
        if (value == null) return "";
        var out = new StringBuilder(value.length());
        for (var c : value.toCharArray()) {
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
