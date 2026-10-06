package com.thaishopfun.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private UserAccount user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
<<<<<<< HEAD
    @Column(name = "created_date_time", nullable = false) private Instant createdDateTime;
    @Column(name = "modified_date_time", nullable = false) private Instant modifiedDateTime;

    protected RefreshToken() {}
    public RefreshToken(UserAccount user, String tokenHash, Instant expiresAt) {
        var now = Instant.now();
=======
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected RefreshToken() {}
    public RefreshToken(UserAccount user, String tokenHash, Instant expiresAt) {
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
        this.id = UUID.randomUUID();
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
<<<<<<< HEAD
        this.createdDateTime = now;
        this.modifiedDateTime = now;
    }
    public UserAccount getUser() { return user; }
    public boolean isUsable() { return revokedAt == null && expiresAt.isAfter(Instant.now()); }
    public void revoke() {
        this.revokedAt = Instant.now();
        this.modifiedDateTime = this.revokedAt;
    }
}
=======
        this.createdAt = Instant.now();
    }
    public UserAccount getUser() { return user; }
    public boolean isUsable() { return revokedAt == null && expiresAt.isAfter(Instant.now()); }
    public void revoke() { revokedAt = Instant.now(); }
}

>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
