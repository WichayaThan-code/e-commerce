package com.thaishopfun.merchant.delivery;

import com.thaishopfun.common.notification.EmailMessage;
import com.thaishopfun.common.notification.NotificationProperties;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Thai e-mail copy for shop application submit (admins) and shop status changes (owners). */
@Component
public class ShopLifecycleMessageTemplates {
    static final String BRAND = "ThaiShopFun";

    private final NotificationProperties properties;

    public ShopLifecycleMessageTemplates(NotificationProperties properties) {
        this.properties = properties;
    }

    public String adminLoginUrl(long shopId) {
        var next = "/admin/store-applications?shop=" + shopId;
        return properties.frontendBaseUrl() + "/login?next=" + URLEncoder.encode(next, StandardCharsets.UTF_8);
    }

    public String ownerLoginUrl(ShopLifecycleAction action) {
        var next = action == ShopLifecycleAction.APPROVED ? "/merchant/dashboard" : "/";
        return properties.frontendBaseUrl() + "/login?next=" + URLEncoder.encode(next, StandardCharsets.UTF_8);
    }

    public EmailMessage applicationSubmittedToAdmin(
        String to,
        String toName,
        String applicantUserLabel,
        String shopName,
        long shopId,
        String applicantEmail
    ) {
        var loginUrl = adminLoginUrl(shopId);
        var subject = "[" + BRAND + "] มีคำขอสมัครร้านค้าใหม่ — " + safe(shopName);
        var greeting = greeting(toName);
        var userLabel = safe(blankTo(applicantUserLabel, "ผู้ใช้"));
        var shop = safe(shopName);
        var emailLine = applicantEmail == null || applicantEmail.isBlank()
            ? ""
            : "\nอีเมลผู้สมัคร: " + applicantEmail.trim();
        var text = """
            %s

            มี User %s ส่งสมัครร้าน %s เข้ามาในระบบ
            รหัสร้าน (shop): %d%s

            กรุณาเข้าสู่ระบบเพื่อไปอนุมัติหรือตรวจสอบคำขอ:
            %s

            ทีมงาน %s
            """.formatted(greeting, userLabel, shop, shopId, emailLine, loginUrl, BRAND);
        var htmlEmail = applicantEmail == null || applicantEmail.isBlank()
            ? ""
            : "<p style=\"margin:0 0 8px\">อีเมลผู้สมัคร: <strong>" + escape(applicantEmail.trim()) + "</strong></p>";
        var html = layout(subject, """
            <p style="margin:0 0 16px">%s</p>
            <p style="margin:0 0 12px">มี User <strong>%s</strong> ส่งสมัครร้าน <strong>%s</strong> เข้ามาในระบบ</p>
            <p style="margin:0 0 8px">รหัสร้าน (shop): <strong>%d</strong></p>
            %s
            <p style="margin:16px 0 20px"><a href="%s" style="display:inline-block;padding:12px 24px;background:#1268d7;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:600">เข้าสู่ระบบเพื่อตรวจสอบ</a></p>
            <p style="margin:0;color:#666;font-size:13px">หากปุ่มใช้ไม่ได้ ให้เปิดลิงก์นี้:<br><a href="%s" style="color:#1268d7;word-break:break-all">%s</a></p>
            """.formatted(
            escape(greeting),
            escape(userLabel),
            escape(shop),
            shopId,
            htmlEmail,
            escape(loginUrl),
            escape(loginUrl),
            escape(loginUrl)
        ));
        return new EmailMessage(to, toName, subject, text, html);
    }

    public EmailMessage statusChangedToOwner(
        String to,
        String toName,
        ShopLifecycleAction action,
        String shopName,
        String reason
    ) {
        var loginUrl = ownerLoginUrl(action);
        var shop = safe(shopName);
        var title = switch (action) {
            case APPROVED -> "ร้านค้าของคุณได้รับการอนุมัติแล้ว";
            case REJECTED -> "คำขอเปิดร้านไม่ผ่านการอนุมัติ";
            case SUSPENDED -> "ร้านค้าของคุณถูกระงับชั่วคราว";
            case RESTORED -> "ร้านค้าของคุณถูกปลดระงับแล้ว";
        };
        var subject = "[" + BRAND + "] " + title + " — " + shop;
        var greeting = greeting(toName);
        var detail = switch (action) {
            case APPROVED -> "ร้าน " + shop + " ได้รับการอนุมัติแล้ว คุณสามารถเข้าสู่ระบบเพื่อเริ่มใช้งานเมนูจัดการร้านได้";
            case REJECTED -> "ร้าน " + shop + " ไม่ผ่านการอนุมัติจากผู้ดูแลระบบ จึงยังเข้าเมนูจัดการร้านไม่ได้";
            case SUSPENDED -> "ร้าน " + shop + " ถูกระงับชั่วคราว จึงยังเข้าเมนูจัดการร้านไม่ได้จนกว่าจะได้รับการปลดระงับ";
            case RESTORED -> "ร้าน " + shop + " ถูกปลดระงับแล้ว คุณสามารถเข้าสู่ระบบเพื่อใช้งานเมนูจัดการร้านต่อได้";
        };
        var reasonBlock = reason == null || reason.isBlank() ? "" : "\nเหตุผล: " + reason.trim();
        var text = """
            %s

            %s%s

            เข้าสู่ระบบ:
            %s

            ทีมงาน %s
            """.formatted(greeting, detail, reasonBlock, loginUrl, BRAND);
        var reasonHtml = reason == null || reason.isBlank()
            ? ""
            : "<p style=\"margin:0 0 12px\">เหตุผล: <strong>" + escape(reason.trim()) + "</strong></p>";
        var buttonLabel = action == ShopLifecycleAction.APPROVED || action == ShopLifecycleAction.RESTORED
            ? "เข้าสู่ระบบร้านค้า"
            : "เข้าสู่ระบบ";
        var html = layout(subject, """
            <p style="margin:0 0 16px">%s</p>
            <p style="margin:0 0 12px">%s</p>
            %s
            <p style="margin:16px 0 20px"><a href="%s" style="display:inline-block;padding:12px 24px;background:#1268d7;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:600">%s</a></p>
            <p style="margin:0;color:#666;font-size:13px">หากปุ่มใช้ไม่ได้ ให้เปิดลิงก์นี้:<br><a href="%s" style="color:#1268d7;word-break:break-all">%s</a></p>
            """.formatted(
            escape(greeting),
            escape(detail),
            reasonHtml,
            escape(loginUrl),
            escape(buttonLabel),
            escape(loginUrl),
            escape(loginUrl)
        ));
        return new EmailMessage(to, toName, subject, text, html);
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

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String escape(String value) {
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
