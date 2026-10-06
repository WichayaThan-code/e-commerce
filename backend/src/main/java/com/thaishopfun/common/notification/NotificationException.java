package com.thaishopfun.common.notification;

/** Delivery of an e-mail or SMS failed. Messages must not contain OTP codes or tokens. */
public class NotificationException extends RuntimeException {
    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
