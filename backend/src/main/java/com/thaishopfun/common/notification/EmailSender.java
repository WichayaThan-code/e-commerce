package com.thaishopfun.common.notification;

/**
 * Delivers e-mail. Implementations: {@link BrevoEmailSender} ({@code NOTIFICATION_EMAIL_PROVIDER=brevo}, HTTP API),
 * {@link SmtpEmailSender} ({@code smtp}) and {@link LoggingEmailSender} ({@code log}, the default when neither
 * {@code BREVO_API_KEY} nor {@code MAIL_HOST} is set).
 * Implementations throw {@link NotificationException} when delivery fails.
 */
public interface EmailSender {
    void send(EmailMessage message);

    /** Short provider name for logs, e.g. {@code brevo}, {@code smtp} or {@code log}. */
    String provider();
}
