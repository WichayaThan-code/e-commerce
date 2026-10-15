package com.thaishopfun.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AdminManagementDtos {
    private AdminManagementDtos() {}

    public record CreateUserRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "^[0-9+ -]{9,20}$", message = "เบอร์โทรศัพท์ไม่ถูกต้อง") String phone
    ) {}

    public record UpdateUserRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Pattern(regexp = "^[0-9+ -]{9,20}$", message = "เบอร์โทรศัพท์ไม่ถูกต้อง") String phone
    ) {}

    public record AssignRoleRequest(String roleCode) {}

    public record RoleRequest(
        @Pattern(regexp = "^(PLATFORM|STORE)$", message = "บริบทของบทบาทไม่ถูกต้อง") String context,
        @NotBlank @Size(max = 150) String nameTh,
        @Size(max = 500) String descriptionTh,
        @NotNull @Size(max = 100) List<@NotBlank @Size(max = 80) String> permissions
    ) {}

    public record ShopRoleAssignmentRequest(
        @NotBlank @Size(max = 40) String roleCode
    ) {}
}
