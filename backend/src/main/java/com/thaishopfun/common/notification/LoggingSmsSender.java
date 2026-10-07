package com.thaishopfun.common.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default SMS sender ({@code SMS_PROVIDER=log}): no SMS gateway is called, the masked destination is
 * logged. The text (contains the OTP) is logged at DEBUG only.
 */
public class LoggingSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public void send(SmsMessage message) {
        log.info("[sms:log] SMS would be sent to={} ({} chars) - no SMS provider configured (SMS_PROVIDER=log)",
            Masking.phone(message.to()), message.text().length());
        if (log.isDebugEnabled()) {
            log.debug("[sms:log] text for {}: {}", Masking.phone(message.to()), message.text());
        }
    }

    @Override
    public String provider() {
        return "log";
    }
}
