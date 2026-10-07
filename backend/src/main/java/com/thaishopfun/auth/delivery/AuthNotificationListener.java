package com.thaishopfun.auth.delivery;

import com.thaishopfun.auth.OtpChannel;
import com.thaishopfun.common.notification.EmailSender;
import com.thaishopfun.common.notification.Masking;
import com.thaishopfun.common.notification.SmsSender;
import com.thaishopfun.common.notification.NotificationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Delivers OTP codes and password-reset links. Runs only AFTER the issuing transaction commits
 * (a rollback never sends) and on the bounded notification executor, so API latency is unaffected.
 * Failures are logged; the API response has already been returned and does not change.
 */
@Component
public class AuthNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(AuthNotificationListener.class);

    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final AuthMessageTemplates templates;

    public AuthNotificationListener(EmailSender emailSender, SmsSender smsSender, AuthMessageTemplates templates) {
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.templates = templates;
    }

    @Async(NotificationConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOtpIssued(OtpIssuedEvent event) {
        try {
            if (event.channel() == OtpChannel.EMAIL) {
                emailSender.send(templates.otpEmail(event.destination(), event.recipientName(), event.purpose(), event.code(), event.ttlMinutes()));
                log.info("OTP delivered id={} purpose={} channel=EMAIL provider={} to={}",
                    event.otpId(), event.purpose(), emailSender.provider(), Masking.email(event.destination()));
            } else {
                smsSender.send(templates.otpSms(event.destination(), event.purpose(), event.code(), event.ttlMinutes()));
                log.info("OTP delivered id={} purpose={} channel=SMS provider={} to={}",
                    event.otpId(), event.purpose(), smsSender.provider(), Masking.phone(event.destination()));
            }
        } catch (RuntimeException exception) {
            var to = event.channel() == OtpChannel.EMAIL ? Masking.email(event.destination()) : Masking.phone(event.destination());
            log.error("OTP delivery FAILED id={} purpose={} channel={} to={}: {}",
                event.otpId(), event.purpose(), event.channel(), to, exception.getMessage(), exception);
        }
    }

    @Async(NotificationConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        try {
            emailSender.send(templates.passwordResetEmail(event.email(), event.recipientName(), event.token(), event.ttlMinutes()));
            log.info("Password reset link delivered userId={} provider={} to={}",
                event.userId(), emailSender.provider(), Masking.email(event.email()));
        } catch (RuntimeException exception) {
            log.error("Password reset delivery FAILED userId={} to={}: {}",
                event.userId(), Masking.email(event.email()), exception.getMessage(), exception);
        }
    }
}
