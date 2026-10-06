package com.thaishopfun.auth;

import jakarta.validation.constraints.*;
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

    public record UserResponse(
        UUID id, String firstName, String lastName, String email, String phone, UserRole role,
        boolean emailVerified, boolean phoneVerified
    ) {
        public static UserResponse from(UserAccount user) {
            return new UserResponse(
                user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getPhone(), user.getRole(),
                user.isEmailVerified(), user.isPhoneVerified()
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