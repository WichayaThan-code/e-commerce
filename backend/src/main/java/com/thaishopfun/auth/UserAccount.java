package com.thaishopfun.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id private UUID id;
    @Column(name = "first_name", nullable = false) private String firstName;
    @Column(name = "last_name", nullable = false) private String lastName;
    @Column(nullable = false, unique = true) private String email;
    @Column(nullable = false, unique = true) private String phone;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private UserRole role;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private UserStatus status;
<<<<<<< HEAD
    @Column(name = "email_verified_at") private Instant emailVerifiedAt;
    @Column(name = "phone_verified_at") private Instant phoneVerifiedAt;
    @Column(name = "last_login_at") private Instant lastLoginAt;
    @Column(name = "permission_version", nullable = false) private int permissionVersion;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Column(name = "created_date_time", nullable = false) private Instant createdDateTime;
    @Column(name = "modified_date_time", nullable = false) private Instant modifiedDateTime;
=======
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb

    protected UserAccount() {}

    public UserAccount(String firstName, String lastName, String email, String phone, String passwordHash) {
        var now = Instant.now();
        this.id = UUID.randomUUID();
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.role = UserRole.CUSTOMER;
        this.status = UserStatus.ACTIVE;
<<<<<<< HEAD
        this.permissionVersion = 0;
        this.createdDateTime = now;
        this.modifiedDateTime = now;
=======
        this.createdAt = now;
        this.updatedAt = now;
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
    }

    public static UserAccount admin(String username, String passwordHash) {
        var account = new UserAccount("System", "Administrator", username.toLowerCase(), "0000000000", passwordHash);
        account.role = UserRole.ADMIN;
<<<<<<< HEAD
        account.emailVerifiedAt = Instant.now();
        return account;
    }

=======
        return account;
    }

    public void promoteToMerchant() {
        if (role == UserRole.ADMIN) return;
        role = UserRole.MERCHANT;
        updatedAt = Instant.now();
    }

>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
    public UUID getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }
    public UserStatus getStatus() { return status; }
<<<<<<< HEAD
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public Instant getPhoneVerifiedAt() { return phoneVerifiedAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public int getPermissionVersion() { return permissionVersion; }
    public Instant getDeletedAt() { return deletedAt; }

    public boolean isEmailVerified() { return emailVerifiedAt != null; }
    public boolean isPhoneVerified() { return phoneVerifiedAt != null; }
    public boolean isDeleted() { return deletedAt != null; }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.permissionVersion += 1;
        touch();
    }

    public void markEmailVerified() {
        this.emailVerifiedAt = Instant.now();
        touch();
    }

    public void markPhoneVerified() {
        this.phoneVerifiedAt = Instant.now();
        touch();
    }

    public void touchLastLogin() {
        this.lastLoginAt = Instant.now();
        touch();
    }

    private void touch() { this.modifiedDateTime = Instant.now(); }
}
=======
}
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
