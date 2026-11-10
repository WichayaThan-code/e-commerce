package com.thaishopfun.common.notification;

import com.thaishopfun.auth.OtpPurpose;
import com.thaishopfun.auth.delivery.AuthMessageTemplates;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Unit tests for the Brevo HTTP API sender and the brevo provider selection. */
class BrevoEmailSenderTests {
    private static final String KEY = "xkeysib-test-key-0000";
    private final AuthMessageTemplates templates = new AuthMessageTemplates(new NotificationProperties(null, null, null, null, null));

    private record Fixture(BrevoEmailSender sender, MockRestServiceServer server) {}

    private static Fixture fixture() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var sender = new BrevoEmailSender(builder, "https://api.brevo.test/", KEY, "no-reply@thaishopfun.test", "ThaiShopFun");
        return new Fixture(sender, server);
    }

    @Test void postsJsonWithApiKeyHeaderSenderRecipientSubjectAndBothBodies() {
        var f = fixture();
        f.server().expect(requestTo("https://api.brevo.test/v3/smtp/email"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("api-key", KEY))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.sender.email").value("no-reply@thaishopfun.test"))
            .andExpect(jsonPath("$.sender.name").value("ThaiShopFun"))
            .andExpect(jsonPath("$.to[0].email").value("buyer@example.com"))
            .andExpect(jsonPath("$.to[0].name").value("Somchai"))
            .andExpect(jsonPath("$.subject").isNotEmpty())
            .andExpect(jsonPath("$.textContent").value(org.hamcrest.Matchers.containsString("123456")))
            .andExpect(jsonPath("$.htmlContent").value(org.hamcrest.Matchers.containsString("123456")))
            .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{\"messageId\":\"<abc@smtp-relay.mailin.fr>\"}"));

        f.sender().send(templates.otpEmail("buyer@example.com", "Somchai", OtpPurpose.EMAIL_VERIFY, "123456", 5));

        f.server().verify();
        assertEquals("brevo", f.sender().provider());
    }

    @Test void textOnlyMessageOmitsHtmlContentAndRecipientName() {
        var f = fixture();
        f.server().expect(requestTo("https://api.brevo.test/v3/smtp/email"))
            .andExpect(jsonPath("$.to[0].name").doesNotExist())
            .andExpect(jsonPath("$.htmlContent").doesNotExist())
            .andExpect(jsonPath("$.textContent").value("hello"))
            .andRespond(withSuccess());
        f.sender().send(new EmailMessage("a@example.com", null, "Hi", "hello", null));
        f.server().verify();
    }

    @Test void non2xxIsWrappedWithStatusWithoutLeakingKeyOrCode() {
        var f = fixture();
        f.server().expect(requestTo("https://api.brevo.test/v3/smtp/email"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"unauthorized\",\"message\":\"Key not found\"}"));

        var error = assertThrows(NotificationException.class,
            () -> f.sender().send(templates.otpEmail("buyer@example.com", null, OtpPurpose.SIGNUP, "654321", 5)));

        assertTrue(error.getMessage().contains("HTTP 401"), error.getMessage());
        assertTrue(error.getMessage().contains("b***@example.com"), error.getMessage());
        assertFalse(error.getMessage().contains(KEY));
        assertFalse(error.getMessage().contains("654321"));
        assertFalse(error.getMessage().contains("buyer@example.com"));
    }

    @Test void serverErrorIsWrapped() {
        var f = fixture();
        f.server().expect(requestTo("https://api.brevo.test/v3/smtp/email")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        var error = assertThrows(NotificationException.class,
            () -> f.sender().send(new EmailMessage("a@example.com", null, "Hi", "hello", null)));
        assertTrue(error.getMessage().contains("HTTP 502"), error.getMessage());
    }

    @Test void providerSelection() {
        var mailFrom = new NotificationProperties.Mail(null, 587, null, null, "no-reply@thaishopfun.test", "ThaiShopFun", true, false);
        var brevo = new NotificationProperties.Brevo(KEY, null);

        // Blank provider + BREVO_API_KEY -> brevo (even when MAIL_HOST is also set).
        var auto = new NotificationProperties(null, null, null, null, mailFrom, brevo);
        assertEquals("brevo", auto.resolvedEmailProvider());
        var withHost = new NotificationProperties("", null, null, null,
            new NotificationProperties.Mail("smtp.example.com", 587, "u", "p", "a@example.com", null, true, false), brevo);
        assertEquals("brevo", withHost.resolvedEmailProvider());
        // Explicit provider wins.
        assertEquals("smtp", new NotificationProperties("smtp", null, null, null, withHost.mail(), brevo).resolvedEmailProvider());
        assertEquals("log", new NotificationProperties("log", null, null, null, mailFrom, brevo).resolvedEmailProvider());
        // No key, no host -> log (unchanged behaviour).
        assertEquals("log", new NotificationProperties(null, null, null, null, mailFrom, null).resolvedEmailProvider());
        assertEquals("https://api.brevo.com", auto.brevo().baseUrlOrDefault());

        var config = new NotificationConfig();
        assertInstanceOf(BrevoEmailSender.class, config.emailSender(auto));
        assertInstanceOf(BrevoEmailSender.class, config.emailSender(new NotificationProperties("BREVO", null, null, null, mailFrom, brevo)));
    }

    @Test void brevoFailsFastWithoutKeyOrValidSender() {
        var config = new NotificationConfig();
        var mailFrom = new NotificationProperties.Mail(null, 587, null, null, "no-reply@thaishopfun.test", null, true, false);
        var noKey = assertThrows(IllegalStateException.class,
            () -> config.emailSender(new NotificationProperties("brevo", null, null, null, mailFrom, null)));
        assertTrue(noKey.getMessage().contains("BREVO_API_KEY"));

        var brevo = new NotificationProperties.Brevo(KEY, null);
        var noFrom = assertThrows(IllegalStateException.class,
            () -> config.emailSender(new NotificationProperties("brevo", null, null, null, null, brevo)));
        assertTrue(noFrom.getMessage().contains("MAIL_FROM"));
        assertFalse(noFrom.getMessage().contains(KEY));

        var badFrom = new NotificationProperties.Mail(null, 587, null, null, "not-an-email", null, true, false);
        assertThrows(IllegalStateException.class,
            () -> config.emailSender(new NotificationProperties("brevo", null, null, null, badFrom, brevo)));
    }
}
