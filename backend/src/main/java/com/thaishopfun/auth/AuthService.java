package com.thaishopfun.auth;

<<<<<<< HEAD
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
=======
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
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
<<<<<<< HEAD
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String FORGOT_MESSAGE = "หากอีเมลนี้มีในระบบ คุณจะได้รับลิงก์รีเซ็ตรหัสผ่านในไม่ช้า";

=======
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
    public record Session(AuthDtos.AuthResponse response, String refreshToken) {}

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
<<<<<<< HEAD
    private final PasswordResetTokenRepository passwordResetTokens;
    private final OtpVerificationRepository otpVerifications;
=======
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
    private final PasswordEncoder passwords;
    private final AuthenticationManager authentication;
    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;
<<<<<<< HEAD
    private final LoginAttemptService loginAttempts;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserAccountRepository users, RefreshTokenRepository refreshTokens,
                       PasswordResetTokenRepository passwordResetTokens, OtpVerificationRepository otpVerifications,
                       PasswordEncoder passwords, AuthenticationManager authentication, JwtEncoder jwtEncoder,
                       SecurityProperties properties, LoginAttemptService loginAttempts) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordResetTokens = passwordResetTokens;
        this.otpVerifications = otpVerifications;
=======
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserAccountRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwords,
                       AuthenticationManager authentication, JwtEncoder jwtEncoder, SecurityProperties properties) {
        this.users = users;
        this.refreshTokens = refreshTokens;
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
        this.passwords = passwords;
        this.authentication = authentication;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
<<<<<<< HEAD
        this.loginAttempts = loginAttempts;
=======
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
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
<<<<<<< HEAD
        loginAttempts.assertNotLocked(lookup);
        try {
            authentication.authenticate(new UsernamePasswordAuthenticationToken(lookup, request.password()));
        } catch (BadCredentialsException exception) {
            loginAttempts.recordFailure(lookup);
            throw exception;
        }
        loginAttempts.recordSuccess(lookup);
        var user = users.findByEmailIgnoreCaseOrPhone(lookup, lookup)
            .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "ข้อมูลเข้าสู่ระบบไม่ถูกต้อง"));
        if (user.isDeleted()) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "ข้อมูลเข้าสู่ระบบไม่ถูกต้อง");
        }
        user.touchLastLogin();
=======
        authentication.authenticate(new UsernamePasswordAuthenticationToken(lookup, request.password()));
        var user = users.findByEmailIgnoreCaseOrPhone(lookup, lookup)
            .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "ข้อมูลเข้าสู่ระบบไม่ถูกต้อง"));
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
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

<<<<<<< HEAD
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
        log.info("Password reset token issued for userId={} (dev delivery)", user.getId());
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
        loginAttempts.recordSuccess(user.getEmail());
        loginAttempts.recordSuccess(user.getPhone());
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
        enforceOtpSendLimits(destinationHash, purpose);

        UUID userId = authenticatedUserId;
        if (purpose == OtpPurpose.EMAIL_VERIFY || purpose == OtpPurpose.PHONE_VERIFY) {
            var user = resolveUserForVerify(channel, normalized, authenticatedUserId);
            userId = user.getId();
        } else if (purpose == OtpPurpose.PASSWORD_RESET || purpose == OtpPurpose.SIGNUP) {
            userId = users.findByEmailIgnoreCaseOrPhone(normalized, normalized).map(UserAccount::getId).orElse(null);
        }

        expirePending(destinationHash, purpose);
        var code = String.format("%06d", random.nextInt(1_000_000));
        var expiresAt = Instant.now().plus(Duration.ofMinutes(properties.otpTtlMinutes()));
        var otp = otpVerifications.save(new OtpVerification(
            userId, channel, maskDestination(channel, normalized), destinationHash, purpose,
            passwords.encode(code), expiresAt, properties.otpMaxAttempts()
        ));
        log.info("OTP issued id={} purpose={} channel={} destination={}", otp.getId(), purpose, channel, maskDestination(channel, normalized));
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
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "ไม่พบบัญชีผู้ใช้"));
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

=======
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
    private Session createSession(UserAccount user) {
        var now = Instant.now();
        var accessDuration = Duration.ofMinutes(properties.accessTokenMinutes());
        var claims = JwtClaimsSet.builder()
            .issuer("thaishopfun")
            .issuedAt(now)
            .expiresAt(now.plus(accessDuration))
            .subject(user.getId().toString())
            .claim("roles", List.of(user.getRole().name()))
<<<<<<< HEAD
            .claim("pv", user.getPermissionVersion())
=======
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
            .build();
        var accessToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        var bytes = new byte[32];
        random.nextBytes(bytes);
        var rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user, hash(rawRefresh), now.plus(Duration.ofDays(properties.refreshTokenDays()))));
        var response = new AuthDtos.AuthResponse(accessToken, accessDuration.toSeconds(), AuthDtos.UserResponse.from(user));
        return new Session(response, rawRefresh);
    }

<<<<<<< HEAD
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

=======
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
    private String normalizePhone(String value) { return value.replaceAll("[ -]", ""); }
    private AuthException invalidRefresh() { return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "เซสชันหมดอายุ กรุณาเข้าสู่ระบบใหม่"); }
    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
<<<<<<< HEAD
}
=======
}

>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
