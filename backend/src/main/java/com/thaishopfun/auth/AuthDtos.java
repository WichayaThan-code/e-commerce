package com.thaishopfun.auth;

import com.thaishopfun.auth.authz.AuthorizationService;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "^[0-9+ -]{9,20}$", message = "เบอร์โทรศัพท์ไม่ถูกต้อง") String phone,
        @NotBlank @Size(min = 8, max = 72) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "รหัสผ่านต้องมีตัวอักษรและตัวเลข") String password
    ) {}

    public record LoginRequest(@NotBlank String identity, @NotBlank String password) {}

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 255) String email) {}

    public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 72) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "รหัสผ่านต้องมีตัวอักษรและตัวเลข") String newPassword
    ) {}

    public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 72) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "รหัสผ่านต้องมีตัวอักษรและตัวเลข") String newPassword
    ) {}

    public record SendOtpRequest(
        @NotBlank String destination,
        @NotNull OtpPurpose purpose
    ) {}

    public record VerifyOtpRequest(
        @NotNull UUID otpId,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "รหัส OTP ต้องเป็นตัวเลข 6 หลัก") String code
    ) {}

    /**
     * Current user. {@code role} (CUSTOMER / MERCHANT / ADMIN) is derived from the role tables, not stored (users.role
     * was dropped in V18): ADMIN = any platform permission (active platform_roles row), MERCHANT = active membership in
     * at least one shop, else CUSTOMER. Kept for frontend compatibility (labels / landing page), never for authorization.
     * {@code roleCodes}, {@code permissions} (account + platform level) and {@code shopPermissions} (per shop the user
     * is an active member of) are computed from the database permission matrix at the time of the response.
     * {@code superAdmin} is true for an active PLATFORM_ADMIN: every permission, in every shop, including codes added
     * to the catalog later (the lists then contain every active code).
     */
    public record UserResponse(
        UUID id, String firstName, String lastName, String email, String phone, UserRole role,
        boolean emailVerified, boolean phoneVerified,
        List<String> roleCodes, List<String> permissions, List<AuthorizationService.ShopPermissions> shopPermissions,
        boolean superAdmin
    ) {
        public static UserResponse from(UserAccount user, AuthorizationService.EffectivePermissions access) {
            return new UserResponse(
                user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getPhone(),
                UserRole.derive(access.superAdmin() || access.permissions().stream().anyMatch(code -> code.startsWith("PLATFORM_")),
                    !access.shops().isEmpty()),
                user.isEmailVerified(), user.isPhoneVerified(),
                access.roles(), access.permissions(), access.shops(), access.superAdmin()
            );
        }
    }

    public record AuthResponse(String accessToken, long expiresIn, UserResponse user) {}

    public record MessageResponse(String message) {}

    public record ForgotPasswordResponse(String message, String resetToken) {}

    public record SendOtpResponse(UUID otpId, long expiresIn, String destinationMasked, String channel, String code) {}

    public record VerifyOtpResponse(boolean verified, String purpose, String resetToken) {}

    public record ApiError(String code, String message) {}
}