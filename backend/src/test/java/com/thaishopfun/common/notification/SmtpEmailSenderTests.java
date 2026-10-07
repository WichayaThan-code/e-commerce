package com.thaishopfun.common.notification;

import com.thaishopfun.auth.OtpPurpose;
import com.thaishopfun.auth.delivery.AuthMessageTemplates;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests for the SMTP sender (MIME structure / UTF-8) and provider selection. */
class SmtpEmailSenderTests {
    private final NotificationProperties defaults = new NotificationProperties(null, null, null, null, null);
    private final AuthMessageTemplates templates = new AuthMessageTemplates(defaults);

    @Test void buildsUtf8MultipartAlternativeWithThaiSubjectAndFromName() throws Exception {
        var mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        var sender = new SmtpEmailSender(mailSender, "no-reply@thaishopfun.test", "ThaiShopFun");

        sender.send(templates.otpEmail("buyer@example.com", "สมชาย", OtpPurpose.EMAIL_VERIFY, "123456", 5));

        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        var mime = captor.getValue();
        assertEquals("[ThaiShopFun] รหัส OTP สำหรับยืนยันอีเมล", mime.getSubject());
        assertEquals("buyer@example.com", ((jakarta.mail.internet.InternetAddress) mime.getAllRecipients()[0]).getAddress());
        var from = (jakarta.mail.internet.InternetAddress) mime.getFrom()[0];
        assertEquals("no-reply@thaishopfun.test", from.getAddress());
        assertEquals("ThaiShopFun", from.getPersonal());
        var raw = new ByteArrayOutputStream();
        mime.writeTo(raw);
        var wire = raw.toString(StandardCharsets.US_ASCII);
        assertTrue(wire.contains("multipart/alternative"), "expects text + html alternatives");
        assertFalse(wire.contains("multipart/mixed"), "no attachment-style top level");
        assertTrue(wire.toLowerCase().contains("charset=utf-8"));
        assertTrue(mime.getContent() instanceof MimeMultipart);
    }

    @Test void failuresAreWrappedWithoutLeakingTheBody() {
        var mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("connection refused")).when(mailSender).send(any(MimeMessage.class));
        var sender = new SmtpEmailSender(mailSender, "no-reply@thaishopfun.test", "ThaiShopFun");
        var error = assertThrows(NotificationException.class,
            () -> sender.send(templates.otpEmail("buyer@example.com", null, OtpPurpose.SIGNUP, "654321", 5)));
        assertFalse(error.getMessage().contains("654321"));
        assertTrue(error.getMessage().contains("b***@example.com"));
    }

    @Test void providerDefaultsToLogWithoutHostAndSmtpWithHost() {
        assertEquals("log", defaults.resolvedEmailProvider());
        assertEquals("log", defaults.resolvedSmsProvider());
        var withHost = new NotificationProperties("", "log", null, null,
            new NotificationProperties.Mail("smtp.example.com", 587, "u", "p", "a@example.com", null, true, false));
        assertEquals("smtp", withHost.resolvedEmailProvider());
        var forcedLog = new NotificationProperties("log", null, null, null, withHost.mail());
        assertEquals("log", forcedLog.resolvedEmailProvider());

        var config = new NotificationConfig();
        assertInstanceOf(LoggingEmailSender.class, config.emailSender(defaults));
        assertInstanceOf(SmtpEmailSender.class, config.emailSender(withHost));
        assertInstanceOf(LoggingSmsSender.class, config.smsSender(defaults));
        assertThrows(IllegalStateException.class, () -> config.emailSender(new NotificationProperties("smtp", null, null, null, null)));
        assertThrows(IllegalStateException.class, () -> config.smsSender(new NotificationProperties(null, "twilio", null, null, null)));
    }

    @Test void resetLinkUsesFrontendUrlAndPath() {
        var props = new NotificationProperties(null, null, "https://shop.example.com/, http://localhost:5173", "reset-password", null);
        var mail = new AuthMessageTemplates(props).passwordResetEmail("a@example.com", null, "tok_EN-123", 30);
        assertTrue(mail.textBody().contains("https://shop.example.com/reset-password?token=tok_EN-123"), mail.textBody());
        assertTrue(mail.textBody().contains("30 นาที"));
    }

    @Test void maskingHidesSecrets() {
        assertEquals("s***@example.com", Masking.email("somchai@example.com"));
        assertEquals("081****78", Masking.phone("0812345678"));
        assertEquals("******", Masking.secret("123456"));
        assertFalse(new SmsMessage("0812345678", "code 123456").toString().contains("123456"));
    }
}
