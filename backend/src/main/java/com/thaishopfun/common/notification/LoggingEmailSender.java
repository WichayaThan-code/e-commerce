package com.thaishopfun.common.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development fallback used when no SMTP server is configured: nothing leaves the machine, the
 * e-mail is only logged. The body (which contains the OTP / reset link) is logged at DEBUG only;
 * use {@code OTP_EXPOSE_CODE=true} to read codes from the API response during local testing.
 */
public class LoggingEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.info("[email:log] NOT sent (NOTIFICATION_EMAIL_PROVIDER=log) to={} subject={}",
            Masking.email(message.to()), message.subject());
        if (log.isDebugEnabled()) {
            log.debug("[email:log] body for {}:\n{}", Masking.email(message.to()), message.textBody());
        }
    }

    @Override
    public String provider() {
        return "log";
    }
}
