package com.thaishopfun.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Service
public class OAuthAuthService {
    private final OAuthProperties properties;
    private final OAuthTokenVerifier verifier;
    private final OAuthIdentityRepository identities;
    private final UserAccountRepository users;
    private final AuthService auth;
    private final PasswordEncoder passwords;
    private final SecureRandom random = new SecureRandom();

    public OAuthAuthService(
        OAuthProperties properties,
        OAuthTokenVerifier verifier,
        OAuthIdentityRepository identities,
        UserAccountRepository users,
        AuthService auth,
        PasswordEncoder passwords
    ) {
        this.properties = properties;
        this.verifier = verifier;
        this.identities = identities;
        this.users = users;
        this.auth = auth;
        this.passwords = passwords;
    }

    public AuthDtos.OAuthProvidersResponse providers() {
        return properties.providers();
    }

    @Transactional
    public AuthService.Session login(OAuthProvider provider, AuthDtos.OAuthLoginRequest request) {
        if (!properties.enabled(provider)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "OAUTH_PROVIDER_DISABLED", "การเข้าสู่ระบบด้วยผู้ให้บริการนี้ยังไม่เปิดให้บริการ");
        }
        var verified = verifier.verify(provider, request.idToken());
        var existingLink = identities.findByProviderAndProviderSubject(provider, verified.subject());
        if (existingLink.isPresent()) {
            var user = existingLink.get().getUser();
            assertUsable(user);
            user.touchLastLogin();
            return auth.createSession(user);
        }

        var email = verified.email();
        if (email == null || email.isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "OAUTH_EMAIL_REQUIRED", "ไม่พบอีเมลจากผู้ให้บริการ กรุณาอนุญาตการแชร์อีเมลหรือใช้วิธีเข้าสู่ระบบอื่น");
        }

        var byEmail = users.findByEmailIgnoreCaseOrPhone(email, email);
        UserAccount user;
        if (byEmail.isPresent()) {
            user = byEmail.get();
            assertUsable(user);
            if (identities.existsByUserIdAndProvider(user.getId(), provider)) {
                // Another IdP subject already linked for this user+provider — should be rare.
                throw new AuthException(HttpStatus.CONFLICT, "OAUTH_ALREADY_LINKED", "บัญชีนี้เชื่อมต่อผู้ให้บริการนี้อยู่แล้ว");
            }
            if (verified.emailVerified() && !user.isEmailVerified()) {
                user.markEmailVerified();
            }
        } else {
            var names = resolveNames(verified, request.fullName());
            user = users.save(UserAccount.socialAccount(
                names[0],
                names[1],
                email,
                syntheticPhone(),
                passwords.encode(randomPassword()),
                verified.emailVerified()
            ));
        }

        identities.save(new OAuthIdentity(user, provider, verified.subject(), email));
        user.touchLastLogin();
        return auth.createSession(user);
    }

    private void assertUsable(UserAccount user) {
        if (user.isDeleted() || user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "ข้อมูลเข้าสู่ระบบไม่ถูกต้อง");
        }
    }

    private String[] resolveNames(OAuthVerifiedIdentity verified, String fullName) {
        var first = verified.firstName();
        var last = verified.lastName();
        if ((first == null || first.isBlank()) && (last == null || last.isBlank()) && fullName != null && !fullName.isBlank()) {
            var trimmed = fullName.trim().replaceAll("\\s+", " ");
            var space = trimmed.indexOf(' ');
            if (space < 0) {
                first = trimmed;
                last = "";
            } else {
                first = trimmed.substring(0, space);
                last = trimmed.substring(space + 1);
            }
        }
        if (first == null || first.isBlank()) first = "User";
        if (last == null || last.isBlank()) last = "Account";
        return new String[]{trimTo(first, 100), trimTo(last, 100)};
    }

    private static String trimTo(String value, int max) {
        var trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /** Unique placeholder satisfying users.phone NOT NULL + UNIQUE (max 30 chars). */
    private String syntheticPhone() {
        var hex = UUID.randomUUID().toString().replace("-", "");
        return "oauth:" + hex.substring(0, 24);
    }

    private String randomPassword() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
