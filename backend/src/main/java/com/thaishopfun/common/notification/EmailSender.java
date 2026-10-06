package com.thaishopfun.common.notification;

/**
 * Delivers e-mail. Implementations: {@link SmtpEmailSender} ({@code NOTIFICATION_EMAIL_PROVIDER=smtp})
 * and {@link LoggingEmailSender} ({@code log}, the default when {@code MAIL_HOST} is blank).
 * Implementations throw {@link NotificationException} when delivery fails.
 */
public interface EmailSender {
    void send(EmailMessage message);

    /** Short provider name for logs, e.g. {@code smtp} or {@code log}. */
    String provider();
}
