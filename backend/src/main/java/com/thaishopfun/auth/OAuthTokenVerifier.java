package com.thaishopfun.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies Google ID tokens (JWKS) and Facebook access tokens (Graph API).
 */
@Component
public class OAuthTokenVerifier {
    private static final Logger log = LoggerFactory.getLogger(OAuthTokenVerifier.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";

    private final OAuthProperties properties;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Map<String, JwtDecoder> decoders = new ConcurrentHashMap<>();

    public OAuthTokenVerifier(OAuthProperties properties) {
        this.properties = properties;
    }

    public OAuthVerifiedIdentity verify(OAuthProvider provider, String token) {
        if (token == null || token.isBlank()) {
            throw invalidToken();
        }
        return switch (provider) {
            case GOOGLE -> verifyGoogle(token.trim());
            case FACEBOOK -> verifyFacebook(token.trim());
        };
    }

    private OAuthVerifiedIdentity verifyGoogle(String idToken) {
        var jwt = decodeJwt(
            GOOGLE_JWKS,
            idToken,
            List.of("https://accounts.google.com", "accounts.google.com"),
            properties.googleClientId()
        );
        return fromJwt(jwt, claimEmailVerified(jwt));
    }

    private OAuthVerifiedIdentity verifyFacebook(String accessToken) {
        try {
            var appId = properties.facebookAppId();
            var appSecret = properties.facebookAppSecret();
            var debugUrl = "https://graph.facebook.com/debug_token?input_token="
                + URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
                + "&access_token=" + URLEncoder.encode(appId + "|" + appSecret, StandardCharsets.UTF_8);
            var debug = getJson(debugUrl);
            var data = debug.path("data");
            if (!data.path("is_valid").asBoolean(false) || !appId.equals(data.path("app_id").asText(null))) {
                throw invalidToken();
            }
            var meUrl = "https://graph.facebook.com/me?fields=id,email,first_name,last_name,name&access_token="
                + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
            var me = getJson(meUrl);
            var subject = text(me, "id");
            if (subject == null || subject.isBlank()) throw invalidToken();
            var email = text(me, "email");
            var first = text(me, "first_name");
            var last = text(me, "last_name");
            if ((first == null || first.isBlank()) && (last == null || last.isBlank())) {
                var parts = splitNameParts(text(me, "name"));
                first = parts[0];
                last = parts[1];
            }
            return new OAuthVerifiedIdentity(
                subject,
                email == null ? null : email.trim().toLowerCase(),
                email != null && !email.isBlank(),
                blankToEmpty(first),
                blankToEmpty(last)
            );
        } catch (AuthException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Facebook token verification failed: {}", exception.toString());
            throw invalidToken();
        }
    }

    private Jwt decodeJwt(String jwksUri, String token, List<String> issuers, String audience) {
        try {
            var decoder = decoders.computeIfAbsent(jwksUri + "|" + audience + "|" + String.join(",", issuers), key -> {
                var built = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
                built.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefault(),
                    audienceValidator(audience),
                    issuerValidator(issuers)
                ));
                return built;
            });
            return decoder.decode(token);
        } catch (Exception exception) {
            log.warn("OIDC id_token verification failed: {}", exception.toString());
            throw invalidToken();
        }
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        return jwt -> {
            if (audience == null || audience.isBlank() || !jwt.getAudience().contains(audience)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    private static OAuth2TokenValidator<Jwt> issuerValidator(List<String> issuers) {
        return jwt -> {
            var iss = jwt.getIssuer() == null ? null : stripTrailingSlash(jwt.getIssuer().toString());
            if (iss != null && issuers.stream().map(OAuthTokenVerifier::stripTrailingSlash).anyMatch(iss::equals)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid issuer", null));
        };
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private OAuthVerifiedIdentity fromJwt(Jwt jwt, boolean emailVerified) {
        var subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) throw invalidToken();
        var email = jwt.getClaimAsString("email");
        var first = jwt.getClaimAsString("given_name");
        var last = jwt.getClaimAsString("family_name");
        if ((first == null || first.isBlank()) && (last == null || last.isBlank())) {
            var parts = splitNameParts(jwt.getClaimAsString("name"));
            first = parts[0];
            last = parts[1];
        }
        return new OAuthVerifiedIdentity(
            subject,
            email == null || email.isBlank() ? null : email.trim().toLowerCase(),
            emailVerified && email != null && !email.isBlank(),
            blankToEmpty(first),
            blankToEmpty(last)
        );
    }

    private static boolean claimEmailVerified(Jwt jwt) {
        Object value = jwt.getClaims().get("email_verified");
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String text) return "true".equalsIgnoreCase(text);
        return jwt.getClaimAsString("email") != null && !jwt.getClaimAsString("email").isBlank();
    }

    private JsonNode getJson(String url) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return JSON.readTree(response.body());
    }

    private static String text(JsonNode node, String field) {
        var value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static String[] splitNameParts(String fullName) {
        if (fullName == null || fullName.isBlank()) return new String[]{"", ""};
        var trimmed = fullName.trim().replaceAll("\\s+", " ");
        var space = trimmed.indexOf(' ');
        if (space < 0) return new String[]{trimmed, ""};
        return new String[]{trimmed.substring(0, space), trimmed.substring(space + 1)};
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static AuthException invalidToken() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_OAUTH_TOKEN", "โทเคนเข้าสู่ระบบจากผู้ให้บริการไม่ถูกต้องหรือหมดอายุ");
    }
}
