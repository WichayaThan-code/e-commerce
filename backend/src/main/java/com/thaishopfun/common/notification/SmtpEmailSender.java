package com.thaishopfun.common.notification;

import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Sends UTF-8 multipart/alternative (plain text + HTML) e-mail through SMTP via {@link JavaMailSender}.
 * Works with any SMTP relay: Gmail (app password), company SMTP, SendGrid SMTP (user {@code apikey}).
 */
public class SmtpEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;

    public SmtpEmailSender(JavaMailSender mailSender, String fromAddress, String fromName) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            var mime = mailSender.createMimeMessage();
            var multipart = message.htmlBody() != null && !message.htmlBody().isBlank();
            // RELATED (not the default MIXED) so clients don't show the mail as having an attachment.
            var helper = multipart
                ? new MimeMessageHelper(mime, MimeMessageHelper.MULTIPART_MODE_RELATED, StandardCharsets.UTF_8.name())
                : new MimeMessageHelper(mime, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress, fromName);
            if (message.toName() != null && !message.toName().isBlank()) {
                helper.setTo(new jakarta.mail.internet.InternetAddress(message.to(), message.toName(), StandardCharsets.UTF_8.name()));
            } else {
                helper.setTo(message.to());
            }
            helper.setSubject(message.subject());
            if (multipart) {
                helper.setText(message.textBody(), message.htmlBody());
            } else {
                helper.setText(message.textBody(), false);
            }
            mailSender.send(mime);
            log.info("[email:smtp] sent to={} subject={}", Masking.email(message.to()), message.subject());
        } catch (MailException | MessagingException | UnsupportedEncodingException exception) {
            throw new NotificationException("SMTP delivery to " + Masking.email(message.to()) + " failed: "
                + exception.getClass().getSimpleName() + ": " + exception.getMessage(), exception);
        }
    }

    @Override
    public String provider() {
        return "smtp";
    }
}
