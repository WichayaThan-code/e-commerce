package com.thaishopfun.common.notification;

import java.util.Objects;

/** A single outgoing SMS. {@link #toString()} never prints the text (it usually carries an OTP). */
public record SmsMessage(String to, String text) {
    public SmsMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(text, "text");
    }

    @Override
    public String toString() {
        return "SmsMessage[to=" + Masking.phone(to) + "]";
    }
}
