package com.thaishopfun.common.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends transactional e-mail through the Brevo HTTP API ({@code POST /v3/smtp/email}) instead of SMTP.
 * Used where outbound SMTP is blocked (e.g. Railway Free/Trial/Hobby plans). Only HTTPS (443) is needed.
 *
 * <p>The API key is sent in the {@code api-key} header and is never logged or put in exception messages.</p>
 */
public class BrevoEmailSender implements EmailSender {
    public static final String DEFAULT_BASE_URL = "https://api.brevo.com";
    static final String SEND_PATH = "/v3/smtp/email";
    private static final Logger log = LoggerFactory.getLogger(BrevoEmailSender.class);
    private static final int MAX_ERROR_BODY = 300;

    private final RestClient client;
    private final String fromAddress;
    private final String fromName;

    public BrevoEmailSender(RestClient.Builder builder, String baseUrl, String apiKey, String fromAddress, String fromName) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("Brevo API key is required");
        this.client = builder
            .baseUrl(stripTrailingSlash(baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim()))
            .defaultHeader("api-key", apiKey.trim())
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .build();
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    /** RestClient builder with the same timeouts as the SMTP sender (10 s connect, 15 s read). */
    public static RestClient.Builder defaultClientBuilder() {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(factory);
    }

    @Override
    public void send(EmailMessage message) {
        var body = new LinkedHashMap<String, Object>();
        body.put("sender", Map.of("name", fromName, "email", fromAddress));
        var to = new LinkedHashMap<String, String>();
        to.put("email", message.to());
        if (message.toName() != null && !message.toName().isBlank()) to.put("name", message.toName());
        body.put("to", List.of(to));
        body.put("subject", message.subject());
        body.put("textContent", message.textBody());
        if (message.htmlBody() != null && !message.htmlBody().isBlank()) body.put("htmlContent", message.htmlBody());
        try {
            client.post()
                .uri(SEND_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
            log.info("[email:brevo] sent to={} subject={}", Masking.email(message.to()), message.subject());
        } catch (RestClientResponseException exception) {
            throw new NotificationException("Brevo delivery to " + Masking.email(message.to()) + " failed: HTTP "
                + exception.getStatusCode().value() + " " + truncate(exception.getResponseBodyAsString()), exception);
        } catch (RestClientException exception) {
            throw new NotificationException("Brevo delivery to " + Masking.email(message.to()) + " failed: "
                + exception.getClass().getSimpleName() + ": " + exception.getMessage(), exception);
        }
    }

    @Override
    public String provider() {
        return "brevo";
    }

    private static String truncate(String value) {
        if (value == null) return "";
        var oneLine = value.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= MAX_ERROR_BODY ? oneLine : oneLine.substring(0, MAX_ERROR_BODY) + "...";
    }

    private static String stripTrailingSlash(String value) {
        var result = value;
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
}
