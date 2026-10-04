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
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

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
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static UserAccount admin(String username, String passwordHash) {
        var account = new UserAccount("System", "Administrator", username.toLowerCase(), "0000000000", passwordHash);
        account.role = UserRole.ADMIN;
        return account;
    }

    public void promoteToMerchant() {
        if (role == UserRole.ADMIN) return;
        role = UserRole.MERCHANT;
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }
    public UserStatus getStatus() { return status; }
}
