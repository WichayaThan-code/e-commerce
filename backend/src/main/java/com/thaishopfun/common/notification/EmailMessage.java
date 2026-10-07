package com.thaishopfun.common.notification;

import java.util.Objects;

/**
 * A single outgoing e-mail. Bodies may contain one-time secrets (OTP codes, reset links), so
 * {@link #toString()} deliberately never prints them.
 */
public record EmailMessage(String to, String toName, String subject, String textBody, String htmlBody) {
    public EmailMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(textBody, "textBody");
    }

    @Override
    public String toString() {
        return "EmailMessage[to=" + Masking.email(to) + ", subject=" + subject + "]";
    }
}
