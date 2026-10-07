package com.thaishopfun.common.notification;

/**
 * Delivers SMS. Only {@link LoggingSmsSender} ({@code SMS_PROVIDER=log}) ships today. To add a real
 * gateway (e.g. ThaiBulkSMS, Twilio), implement this interface and register it in
 * {@link NotificationConfig#smsSender}. Implementations throw {@link NotificationException} on failure.
 */
public interface SmsSender {
    void send(SmsMessage message);

    /** Short provider name for logs, e.g. {@code log}. */
    String provider();
}
