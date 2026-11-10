package com.thaishopfun.common.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * {@code app.notification.*} - bound from environment variables in {@code application.yml}
 * (NOTIFICATION_EMAIL_PROVIDER, SMS_PROVIDER, APP_FRONTEND_URL, MAIL_*, BREVO_*).
 */
@ConfigurationProperties("app.notification")
public record NotificationProperties(
    String emailProvider,
    String smsProvider,
    String frontendUrl,
    String resetPasswordPath,
    Mail mail,
    Brevo brevo
) {
    @ConstructorBinding
    public NotificationProperties {
        if (mail == null) mail = new Mail(null, 587, null, null, null, null, true, false);
        if (brevo == null) brevo = new Brevo(null, null);
    }

    /** Convenience constructor without Brevo settings (tests / SMTP-only setups). */
    public NotificationProperties(String emailProvider, String smsProvider, String frontendUrl, String resetPasswordPath, Mail mail) {
        this(emailProvider, smsProvider, frontendUrl, resetPasswordPath, mail, null);
    }

    /** Brevo HTTP e-mail API ({@code BREVO_API_KEY}, optional {@code BREVO_API_URL}). */
    public record Brevo(String apiKey, String baseUrl) {
        public boolean hasApiKey() { return apiKey != null && !apiKey.isBlank(); }
        public String baseUrlOrDefault() { return baseUrl == null || baseUrl.isBlank() ? BrevoEmailSender.DEFAULT_BASE_URL : baseUrl.trim(); }
    }

    public record Mail(
        String host,
        Integer port,
        String username,
        String password,
        String from,
        String fromName,
        Boolean starttls,
        Boolean ssl
    ) {
        public boolean hasHost() { return host != null && !host.isBlank(); }
        public int portOrDefault() { return port == null ? 587 : port; }
        public boolean starttlsEnabled() { return starttls == null || starttls; }
        public boolean sslEnabled() { return ssl != null && ssl; }
        public String fromNameOrDefault() { return fromName == null || fromName.isBlank() ? "ThaiShopFun" : fromName.trim(); }
    }

    /**
     * {@code brevo}, {@code smtp} or {@code log}. Blank means: {@code brevo} when BREVO_API_KEY is set,
     * else {@code smtp} when MAIL_HOST is set, otherwise {@code log}.
     */
    public String resolvedEmailProvider() {
        if (emailProvider == null || emailProvider.isBlank()) {
            if (brevo.hasApiKey()) return "brevo";
            return mail.hasHost() ? "smtp" : "log";
        }
        return emailProvider.trim().toLowerCase();
    }

    public String resolvedSmsProvider() {
        return smsProvider == null || smsProvider.isBlank() ? "log" : smsProvider.trim().toLowerCase();
    }

    /** Base URL of the web frontend without trailing slash (first entry if a comma list is given). */
    public String frontendBaseUrl() {
        var value = frontendUrl == null || frontendUrl.isBlank() ? "http://localhost:5173" : frontendUrl;
        value = value.split(",")[0].trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    public String resetPasswordPathOrDefault() {
        var path = resetPasswordPath == null || resetPasswordPath.isBlank() ? "/reset-password" : resetPasswordPath.trim();
        return path.startsWith("/") ? path : "/" + path;
    }
}
