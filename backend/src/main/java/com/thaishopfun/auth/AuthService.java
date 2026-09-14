package com.thaishopfun.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {
    public record Session(AuthDtos.AuthResponse response, String refreshToken) {}

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final AuthenticationManager authentication;
    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserAccountRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwords,
                       AuthenticationManager authentication, JwtEncoder jwtEncoder, SecurityProperties properties) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.authentication = authentication;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    @Transactional
    public Session register(AuthDtos.RegisterRequest request) {
        var email = request.email().trim().toLowerCase();
        var phone = normalizePhone(request.phone());
        if (users.existsByEmailIgnoreCase(email)) throw new AuthException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "อีเมลนี้ถูกใช้งานแล้ว");
        if (users.existsByPhone(phone)) throw new AuthException(HttpStatus.CONFLICT, "PHONE_EXISTS", "เบอร์โทรศัพท์นี้ถูกใช้งานแล้ว");
        var user = users.save(new UserAccount(request.firstName().trim(), request.lastName().trim(), email, phone, passwords.encode(request.password())));
        return createSession(user);
    }

    @Transactional
    public Session login(AuthDtos.LoginRequest request) {
        var identity = request.identity().trim();
        var lookup = identity.contains("@") ? identity.toLowerCase() : normalizePhone(identity);
        authentication.authenticate(new UsernamePasswordAuthenticationToken(lookup, request.password()));
        var user = users.findByEmailIgnoreCaseOrPhone(lookup, lookup)
            .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "ข้อมูลเข้าสู่ระบบไม่ถูกต้อง"));
        return createSession(user);
    }

    @Transactional
    public Session refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw invalidRefresh();
        var stored = refreshTokens.findByTokenHash(hash(rawToken)).orElseThrow(this::invalidRefresh);
        if (!stored.isUsable()) throw invalidRefresh();
        stored.revoke();
        return createSession(stored.getUser());
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(RefreshToken::revoke);
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserResponse me(UUID id) {
        return users.findById(id).map(AuthDtos.UserResponse::from)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "ไม่พบบัญชีผู้ใช้"));
    }

    private Session createSession(UserAccount user) {
        var now = Instant.now();
        var accessDuration = Duration.ofMinutes(properties.accessTokenMinutes());
        var claims = JwtClaimsSet.builder()
            .issuer("thaishopfun")
            .issuedAt(now)
            .expiresAt(now.plus(accessDuration))
            .subject(user.getId().toString())
            .claim("roles", List.of(user.getRole().name()))
            .build();
        var accessToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        var bytes = new byte[32];
        random.nextBytes(bytes);
        var rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user, hash(rawRefresh), now.plus(Duration.ofDays(properties.refreshTokenDays()))));
        var response = new AuthDtos.AuthResponse(accessToken, accessDuration.toSeconds(), AuthDtos.UserResponse.from(user));
        return new Session(response, rawRefresh);
    }

    private String normalizePhone(String value) { return value.replaceAll("[ -]", ""); }
    private AuthException invalidRefresh() { return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "เซสชันหมดอายุ กรุณาเข้าสู่ระบบใหม่"); }
    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

