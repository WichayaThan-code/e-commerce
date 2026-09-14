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
    public record UserResponse(UUID id, String firstName, String lastName, String email, String phone, UserRole role) {
        public static UserResponse from(UserAccount user) {
            return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getPhone(), user.getRole());
        }
    }
    public record AuthResponse(String accessToken, long expiresIn, UserResponse user) {}
    public record ApiError(String code, String message) {}
}

