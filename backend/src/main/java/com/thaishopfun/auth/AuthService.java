package com.thaishopfun.auth;

import com.thaishopfun.auth.authz.AuthorizationService;
import com.thaishopfun.auth.delivery.OtpIssuedEvent;
import com.thaishopfun.auth.delivery.PasswordResetRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
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
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String FORGOT_MESSAGE = "หากอีเมลนี้มีในระบบ คุณจะได้รับลิงก์รีเซ็ตรหัสผ่านในไม่ช้า";

    public record Session(AuthDtos.AuthResponse response, String refreshToken) {}

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final OtpVerificationRepository otpVerifications;
    private final PasswordEncoder passwords;
    private final AuthenticationManager authentication;
    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;
    private final LoginAttemptService loginAttempts;
    private final ApplicationEventPublisher events;
    private final AuthorizationService authorization;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserAccountRepository users, RefreshTokenRepository refreshTokens,
                       PasswordResetTokenRepository passwordResetTokens, OtpVerificationRepository otpVerifications,
                       PasswordEncoder passwords, AuthenticationManager authentication, JwtEncoder jwtEncoder,
                       SecurityProperties properties, LoginAttemptService loginAttempts,
                       ApplicationEventPublisher events, AuthorizationService authorization) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordResetTokens = passwordResetTokens;
        this.otpVerifications = otpVerifications;
        this.passwords = passwords;
        this.authentication = authentication;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.loginAttempts = loginAttempts;
        this.events = events;
        this.authorization = authorization;
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
        // One lockout counter per account (email and phone share it); unknown identifiers are counted
        // under the identifier itself so they lock the same way and reveal nothing.
        var resolved = users.findByEmailIgnoreCaseOrPhone(lookup, lookup);
        var attemptKey = resolved.map(u -> LoginAttemptService.userKey(u.getId()))
            .orElseGet(() -> LoginAttemptService.identifierKey(lookup));
        loginAttempts.assertNotLocked(attemptKey);
        try {
            authentication.authenticate(new UsernamePasswordAuthenticationToken(lookup, request.password()));
        } catch (BadCredentialsException exception) {
            loginAttempts.recordFailure(attemptKey);
            throw exception;
        }
        loginAttempts.recordSuccess(attemptKey);
        var user = resolved
            .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "ข้อมูลเข้าสู่ระบบไม่ถูกต้อง"));
        if (user.isDeleted()) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "ข้อมูลเข้าสู่ระบบไม่ถูกต้อง");
        }
        user.touchLastLogin();
        return createSession(user);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public Session refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw invalidRefresh();
        var stored = refreshTokens.findByTokenHash(hash(rawToken)).orElseThrow(this::invalidRefresh);
        var user = stored.getUser();
        var userId = user.getId();
        var now = Instant.now();
        if (stored.isRevoked()) {
            // Reuse of a rotated/revoked refresh token: assume it leaked and end every session of the user.
            log.warn("Refresh token reuse detected for userId={}; revoking all refresh tokens", userId);
            refreshTokens.revokeAllActiveForUser(userId, now);
            throw invalidRefresh();
        }
        if (stored.isExpired()) throw invalidRefresh();
        if (user.getStatus() != UserStatus.ACTIVE || user.isDeleted()) {
            refreshTokens.revokeAllActiveForUser(userId, now);
            throw invalidRefresh();
        }
        // Conditional UPDATE ... WHERE revoked_at IS NULL: exactly one concurrent caller wins the rotation.
        if (refreshTokens.revokeIfActive(stored.getId(), now) != 1) {
            log.warn("Concurrent reuse of refresh token detected for userId={}; revoking all refresh tokens", userId);
            refreshTokens.revokeAllActiveForUser(userId, now);
            throw invalidRefresh();
        }
        var current = users.findById(userId).orElseThrow(this::invalidRefresh);
        return createSession(current);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(RefreshToken::revoke);
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserResponse me(UUID id) {
        return users.findById(id).map(this::userResponse)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "ไม่พบบัญชีผู้ใช้"));
    }

    @Transactional
    public AuthDtos.ForgotPasswordResponse forgotPassword(AuthDtos.ForgotPasswordRequest request) {
        var email = request.email().trim().toLowerCase();
        var userOpt = users.findByEmailIgnoreCaseOrPhone(email, email);
        if (userOpt.isEmpty() || userOpt.get().isDeleted()) {
            return new AuthDtos.ForgotPasswordResponse(FORGOT_MESSAGE, null);
        }
        var user = userOpt.get();
        passwordResetTokens.invalidateActiveForUser(user.getId(), Instant.now());
        var rawToken = randomToken();
        passwordResetTokens.save(new PasswordResetTokenEntity(
            user.getId(), hash(rawToken), Instant.now().plus(Duration.ofMinutes(properties.passwordResetTtlMinutes()))
        ));
        log.info("Password reset token issued for userId={}; e-mail queued for delivery after commit", user.getId());
        // Delivered by AuthNotificationListener only after this transaction commits.
        events.publishEvent(new PasswordResetRequestedEvent(
            user.getId(), user.getEmail(), user.getFirstName(), rawToken, properties.passwordResetTtlMinutes()));
        var exposed = properties.otpExposeCode() ? rawToken : null;
        return new AuthDtos.ForgotPasswordResponse(FORGOT_MESSAGE, exposed);
    }

    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest request) {
        var stored = passwordResetTokens.findByTokenHash(hash(request.token().trim()))
            .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", "โทเคนรีเซ็ตรหัสผ่านไม่ถูกต้องหรือหมดอายุ"));
        if (!stored.isUsable()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", "โทเคนรีเซ็ตรหัสผ่านไม่ถูกต้องหรือหมดอายุ");
        }
        var user = users.findById(stored.getUserId())
            .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", "โทเคนรีเซ็ตรหัสผ่านไม่ถูกต้องหรือหมดอายุ"));
        user.changePassword(passwords.encode(request.newPassword()));
        stored.markUsed();
        users.flush();
        passwordResetTokens.invalidateActiveForUser(user.getId(), Instant.now());
        refreshTokens.revokeAllActiveForUser(user.getId(), Instant.now());
        loginAttempts.clearUser(user.getId());
    }

    @Transactional
    public void changePassword(UUID userId, AuthDtos.ChangePasswordRequest request) {
        var user = users.findById(userId)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "ไม่พบบัญชีผู้ใช้"));
        if (!passwords.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "รหัสผ่านปัจจุบันไม่ถูกต้อง");
        }
        user.changePassword(passwords.encode(request.newPassword()));
        users.flush();
        refreshTokens.revokeAllActiveForUser(user.getId(), Instant.now());
        passwordResetTokens.invalidateActiveForUser(user.getId(), Instant.now());
    }

    @Transactional
    public AuthDtos.SendOtpResponse sendOtp(AuthDtos.SendOtpRequest request, UUID authenticatedUserId) {
        var purpose = request.purpose();
        if (purpose == OtpPurpose.STEP_UP || purpose == OtpPurpose.LOGIN) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PURPOSE", "ยังไม่รองรับ purpose นี้");
        }
        var destination = request.destination().trim();
        var channel = resolveChannel(destination, purpose);
        var normalized = channel == OtpChannel.EMAIL ? destination.toLowerCase() : normalizePhone(destination);
        var destinationHash = hash(normalized);

        UUID userId = authenticatedUserId;
        String recipientName = null;
        if (purpose == OtpPurpose.EMAIL_VERIFY || purpose == OtpPurpose.PHONE_VERIFY) {
            var user = resolveUserForVerify(channel, normalized, authenticatedUserId);
            if (user == null) {
                // Unknown destination and no session: answer exactly like a successful send but store and
                // send nothing, so this endpoint cannot be used to discover registered emails/phones.
                log.info("OTP send skipped for unknown destination purpose={} channel={}", purpose, channel);
                return new AuthDtos.SendOtpResponse(
                    UUID.randomUUID(),
                    Duration.ofMinutes(properties.otpTtlMinutes()).toSeconds(),
                    maskDestination(channel, normalized),
                    channel.name(),
                    properties.otpExposeCode() ? String.format("%06d", random.nextInt(1_000_000)) : null
                );
            }
            enforceOtpSendLimits(destinationHash, purpose);
            userId = user.getId();
            recipientName = user.getFirstName();
        } else {
            enforceOtpSendLimits(destinationHash, purpose);
            if (purpose == OtpPurpose.PASSWORD_RESET || purpose == OtpPurpose.SIGNUP) {
                var existing = users.findByEmailIgnoreCaseOrPhone(normalized, normalized);
                userId = existing.map(UserAccount::getId).orElse(null);
                recipientName = existing.map(UserAccount::getFirstName).orElse(null);
            }
        }

        expirePending(destinationHash, purpose);
        var code = String.format("%06d", random.nextInt(1_000_000));
        var expiresAt = Instant.now().plus(Duration.ofMinutes(properties.otpTtlMinutes()));
        var otp = otpVerifications.save(new OtpVerification(
            userId, channel, maskDestination(channel, normalized), destinationHash, purpose,
            passwords.encode(code), expiresAt, properties.otpMaxAttempts()
        ));
        log.info("OTP issued id={} purpose={} channel={} destination={}", otp.getId(), purpose, channel, maskDestination(channel, normalized));
        // Delivered (email or SMS) by AuthNotificationListener only after this transaction commits.
        events.publishEvent(new OtpIssuedEvent(
            otp.getId(), channel, purpose, normalized, recipientName, code, properties.otpTtlMinutes()));
        return new AuthDtos.SendOtpResponse(
            otp.getId(),
            Duration.ofMinutes(properties.otpTtlMinutes()).toSeconds(),
            otp.getDestinationMasked(),
            channel.name(),
            properties.otpExposeCode() ? code : null
        );
    }

    @Transactional(noRollbackFor = AuthException.class)
    public AuthDtos.VerifyOtpResponse verifyOtp(AuthDtos.VerifyOtpRequest request) {
        var otp = otpVerifications.findById(request.otpId())
            .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "รหัส OTP ไม่ถูกต้องหรือหมดอายุ"));
        if (otp.getStatus() == OtpStatus.FAILED) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "OTP_LOCKED", "รหัส OTP ถูกใช้ผิดหลายครั้ง กรุณาขอรหัสใหม่");
        }
        if (otp.getStatus() == OtpStatus.VERIFIED) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "OTP_ALREADY_USED", "รหัส OTP ถูกใช้งานแล้ว");
        }
        if (otp.getStatus() != OtpStatus.PENDING) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "รหัส OTP ไม่ถูกต้องหรือหมดอายุ");
        }
        if (otp.getExpiresAt().isBefore(Instant.now())) {
            otp.markExpired();
            throw new AuthException(HttpStatus.BAD_REQUEST, "OTP_EXPIRED", "รหัส OTP หมดอายุแล้ว กรุณาขอรหัสใหม่");
        }
        if (!passwords.matches(request.code(), otp.getCodeHash())) {
            otp.recordFailedAttempt();
            if (otp.getStatus() == OtpStatus.FAILED) {
                throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "OTP_LOCKED", "รหัส OTP ถูกใช้ผิดหลายครั้ง กรุณาขอรหัสใหม่");
            }
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "รหัส OTP ไม่ถูกต้อง");
        }

        otp.markVerified();
        String resetToken = null;
        if (otp.getUserId() != null) {
            users.findById(otp.getUserId()).ifPresent(user -> applyVerification(user, otp.getPurpose(), otp.getChannel()));
        }
        if (otp.getPurpose() == OtpPurpose.PASSWORD_RESET) {
            if (otp.getUserId() == null) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "ไม่พบบัญชีสำหรับรีเซ็ตรหัสผ่าน");
            }
            passwordResetTokens.invalidateActiveForUser(otp.getUserId(), Instant.now());
            var raw = randomToken();
            passwordResetTokens.save(new PasswordResetTokenEntity(
                otp.getUserId(), hash(raw), Instant.now().plus(Duration.ofMinutes(properties.passwordResetTtlMinutes()))
            ));
            resetToken = raw;
        }
        return new AuthDtos.VerifyOtpResponse(true, otp.getPurpose().name(), resetToken);
    }

    private void applyVerification(UserAccount user, OtpPurpose purpose, OtpChannel channel) {
        if (purpose == OtpPurpose.EMAIL_VERIFY || (purpose == OtpPurpose.SIGNUP && channel == OtpChannel.EMAIL)) {
            user.markEmailVerified();
        }
        if (purpose == OtpPurpose.PHONE_VERIFY || (purpose == OtpPurpose.SIGNUP && channel == OtpChannel.SMS)) {
            user.markPhoneVerified();
        }
    }

    private UserAccount resolveUserForVerify(OtpChannel channel, String normalized, UUID authenticatedUserId) {
        if (authenticatedUserId != null) {
            var user = users.findById(authenticatedUserId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "ไม่พบบัญชีผู้ใช้"));
            if (channel == OtpChannel.EMAIL && !user.getEmail().equalsIgnoreCase(normalized)) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "DESTINATION_MISMATCH", "อีเมลไม่ตรงกับบัญชีที่เข้าสู่ระบบ");
            }
            if (channel == OtpChannel.SMS && !user.getPhone().equals(normalized)) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "DESTINATION_MISMATCH", "เบอร์โทรศัพท์ไม่ตรงกับบัญชีที่เข้าสู่ระบบ");
            }
            return user;
        }
        return users.findByEmailIgnoreCaseOrPhone(normalized, normalized)
            .filter(user -> !user.isDeleted())
            .orElse(null);
    }

    private OtpChannel resolveChannel(String destination, OtpPurpose purpose) {
        boolean emailLike = destination.contains("@");
        if (purpose == OtpPurpose.EMAIL_VERIFY && !emailLike) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ต้องใช้อีเมลสำหรับ EMAIL_VERIFY");
        }
        if (purpose == OtpPurpose.PHONE_VERIFY && emailLike) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ต้องใช้เบอร์โทรศัพท์สำหรับ PHONE_VERIFY");
        }
        return emailLike ? OtpChannel.EMAIL : OtpChannel.SMS;
    }

    private void enforceOtpSendLimits(String destinationHash, OtpPurpose purpose) {
        var cooldown = Instant.now().minusSeconds(properties.otpResendCooldownSeconds());
        var recent = otpVerifications.findRecent(destinationHash, purpose, OtpStatus.PENDING);
        if (!recent.isEmpty() && recent.get(0).getCreatedDateTime().isAfter(cooldown)) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED", "ขอรหัส OTP ถี่เกินไป กรุณารอสักครู่");
        }
        var hourAgo = Instant.now().minus(Duration.ofHours(1));
        var count = otpVerifications.countByDestinationHashAndPurposeAndCreatedDateTimeAfter(destinationHash, purpose, hourAgo);
        if (count >= properties.otpMaxPerHour()) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED", "ขอรหัส OTP เกินจำนวนที่อนุญาตต่อชั่วโมง");
        }
    }

    private void expirePending(String destinationHash, OtpPurpose purpose) {
        otpVerifications.findRecent(destinationHash, purpose, OtpStatus.PENDING).forEach(OtpVerification::markExpired);
    }

    private Session createSession(UserAccount user) {
        var now = Instant.now();
        var accessDuration = Duration.ofMinutes(properties.accessTokenMinutes());
        var claims = JwtClaimsSet.builder()
            .issuer("thaishopfun")
            .issuedAt(now)
            .expiresAt(now.plus(accessDuration))
            .subject(user.getId().toString())
            // informational only (legacy users.role); permissions are evaluated live from the database
            .claim("roles", List.of(user.getRole().name()))
            .claim("pv", user.getPermissionVersion())
            .build();
        var accessToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        var bytes = new byte[32];
        random.nextBytes(bytes);
        var rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user, hash(rawRefresh), now.plus(Duration.ofDays(properties.refreshTokenDays()))));
        var response = new AuthDtos.AuthResponse(accessToken, accessDuration.toSeconds(), userResponse(user));
        return new Session(response, rawRefresh);
    }

    private AuthDtos.UserResponse userResponse(UserAccount user) {
        return AuthDtos.UserResponse.from(user, authorization.effectivePermissions(user.getId()));
    }

    private String maskDestination(OtpChannel channel, String normalized) {
        if (channel == OtpChannel.EMAIL) {
            var at = normalized.indexOf('@');
            if (at <= 1) return "***" + normalized.substring(Math.max(at, 0));
            return normalized.charAt(0) + "***" + normalized.substring(at);
        }
        if (normalized.length() < 4) return "***";
        return normalized.substring(0, Math.min(3, normalized.length())) + "****" + normalized.substring(normalized.length() - 2);
    }

    private String randomToken() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
