package com.thaishopfun.merchant.delivery;

import com.thaishopfun.common.notification.EmailSender;
import com.thaishopfun.common.notification.Masking;
import com.thaishopfun.common.notification.NotificationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Delivers shop lifecycle e-mails AFTER the business transaction commits, on the notification executor.
 * Failures are logged only — they must never fail the API response.
 */
@Component
public class ShopLifecycleEmailListener {
    private static final Logger log = LoggerFactory.getLogger(ShopLifecycleEmailListener.class);

    private final EmailSender emailSender;
    private final ShopLifecycleMessageTemplates templates;
    private final ShopAdminRecipientResolver admins;

    public ShopLifecycleEmailListener(
        EmailSender emailSender,
        ShopLifecycleMessageTemplates templates,
        ShopAdminRecipientResolver admins
    ) {
        this.emailSender = emailSender;
        this.templates = templates;
        this.admins = admins;
    }

    @Async(NotificationConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationSubmitted(ShopApplicationSubmittedMailEvent event) {
        var recipients = admins.applicationReviewers();
        if (recipients.isEmpty()) {
            log.warn("Shop application mail skipped shopId={}: no admin reviewers with email", event.shopId());
            return;
        }
        for (var recipient : recipients) {
            try {
                emailSender.send(templates.applicationSubmittedToAdmin(
                    recipient.email(),
                    recipient.name(),
                    event.applicantUserLabel(),
                    event.shopName(),
                    event.shopId(),
                    event.applicantEmail()
                ));
                log.info("Shop application mail delivered shopId={} provider={} to={}",
                    event.shopId(), emailSender.provider(), Masking.email(recipient.email()));
            } catch (RuntimeException exception) {
                log.error("Shop application mail FAILED shopId={} to={}: {}",
                    event.shopId(), Masking.email(recipient.email()), exception.getMessage(), exception);
            }
        }
    }

    @Async(NotificationConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(ShopStatusChangedMailEvent event) {
        if (event.ownerEmail() == null || event.ownerEmail().isBlank()) {
            log.warn("Shop status mail skipped shopId={} action={}: owner email missing",
                event.shopId(), event.action());
            return;
        }
        try {
            emailSender.send(templates.statusChangedToOwner(
                event.ownerEmail(),
                event.ownerName(),
                event.action(),
                event.shopName(),
                event.reason()
            ));
            log.info("Shop status mail delivered shopId={} action={} provider={} to={}",
                event.shopId(), event.action(), emailSender.provider(), Masking.email(event.ownerEmail()));
        } catch (RuntimeException exception) {
            log.error("Shop status mail FAILED shopId={} action={} to={}: {}",
                event.shopId(), event.action(), Masking.email(event.ownerEmail()), exception.getMessage(), exception);
        }
    }
}
