package com.thaishopfun.common.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Chooses the e-mail / SMS providers from configuration and provides the small bounded executor
 * that delivers notifications asynchronously after the DB transaction commits.
 *
 * <p>The SMTP {@code JavaMailSender} is built here (not via {@code spring.mail.*}) so Spring Boot's
 * mail auto-configuration and its connection check never run when SMTP is not configured.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class NotificationConfig {
    public static final String EXECUTOR = "notificationExecutor";
    private static final Logger log = LoggerFactory.getLogger(NotificationConfig.class);

    @Bean
    EmailSender emailSender(NotificationProperties properties) {
        var provider = properties.resolvedEmailProvider();
        switch (provider) {
            case "log" -> {
                log.warn("Email provider: log - e-mails are NOT sent, only logged. Set NOTIFICATION_EMAIL_PROVIDER=brevo "
                    + "(BREVO_API_KEY + MAIL_FROM) or smtp (MAIL_HOST ...) to deliver real e-mail.");
                return new LoggingEmailSender();
            }
            case "smtp" -> {
                var mail = properties.mail();
                if (!mail.hasHost()) {
                    throw new IllegalStateException("NOTIFICATION_EMAIL_PROVIDER=smtp requires MAIL_HOST");
                }
                var sender = new JavaMailSenderImpl();
                sender.setHost(mail.host().trim());
                sender.setPort(mail.portOrDefault());
                sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
                var auth = mail.username() != null && !mail.username().isBlank();
                if (auth) {
                    sender.setUsername(mail.username().trim());
                    sender.setPassword(mail.password());
                }
                var javaMail = sender.getJavaMailProperties();
                javaMail.put("mail.transport.protocol", "smtp");
                javaMail.put("mail.smtp.auth", String.valueOf(auth));
                javaMail.put("mail.smtp.starttls.enable", String.valueOf(mail.starttlsEnabled()));
                javaMail.put("mail.smtp.starttls.required", String.valueOf(mail.starttlsEnabled()));
                javaMail.put("mail.smtp.ssl.enable", String.valueOf(mail.sslEnabled()));
                javaMail.put("mail.smtp.connectiontimeout", "10000");
                javaMail.put("mail.smtp.timeout", "15000");
                javaMail.put("mail.smtp.writetimeout", "15000");
                var from = resolveFrom(mail);
                log.info("Email provider: smtp host={} port={} starttls={} ssl={} auth={} from={}",
                    mail.host().trim(), mail.portOrDefault(), mail.starttlsEnabled(), mail.sslEnabled(), auth, Masking.email(from));
                return new SmtpEmailSender(sender, from, mail.fromNameOrDefault());
            }
            case "brevo" -> {
                var brevo = properties.brevo();
                if (!brevo.hasApiKey()) {
                    throw new IllegalStateException("NOTIFICATION_EMAIL_PROVIDER=brevo requires BREVO_API_KEY");
                }
                var mail = properties.mail();
                var from = mail.from() == null ? "" : mail.from().trim();
                if (!isValidAddress(from)) {
                    throw new IllegalStateException(
                        "NOTIFICATION_EMAIL_PROVIDER=brevo requires MAIL_FROM to be a valid sender address verified in Brevo");
                }
                log.info("Email provider: brevo url={} from={}", brevo.baseUrlOrDefault(), Masking.email(from));
                return new BrevoEmailSender(BrevoEmailSender.defaultClientBuilder(), brevo.baseUrlOrDefault(),
                    brevo.apiKey(), from, mail.fromNameOrDefault());
            }
            default -> throw new IllegalStateException(
                "Unknown NOTIFICATION_EMAIL_PROVIDER '" + provider + "' (supported: brevo, smtp, log)");
        }
    }

    @Bean
    SmsSender smsSender(NotificationProperties properties) {
        var provider = properties.resolvedSmsProvider();
        // Add real gateways here, e.g. case "thaibulksms" -> new ThaiBulkSmsSender(...); case "twilio" -> ...
        if ("log".equals(provider)) {
            log.info("SMS provider: log (no SMS gateway configured; SMS are only logged)");
            return new LoggingSmsSender();
        }
        throw new IllegalStateException("Unknown SMS_PROVIDER '" + provider + "' (supported: log)");
    }

    @Bean(name = EXECUTOR)
    ThreadPoolTaskExecutor notificationExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notify-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        // Saturated: deliver on the caller thread rather than silently dropping an OTP.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        return executor;
    }

    private static boolean isValidAddress(String address) {
        if (address == null || address.isBlank()) return false;
        try {
            new jakarta.mail.internet.InternetAddress(address, true).validate();
            return address.contains("@");
        } catch (jakarta.mail.internet.AddressException exception) {
            return false;
        }
    }

    private static String resolveFrom(NotificationProperties.Mail mail) {
        if (mail.from() != null && !mail.from().isBlank()) return mail.from().trim();
        if (mail.username() != null && mail.username().contains("@")) return mail.username().trim();
        log.warn("MAIL_FROM is not set; using no-reply@thaishopfun.local (most SMTP providers reject unverified senders)");
        return "no-reply@thaishopfun.local";
    }
}
