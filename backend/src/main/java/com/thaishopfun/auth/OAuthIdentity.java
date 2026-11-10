package com.thaishopfun.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oauth_identities")
public class OAuthIdentity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private UserAccount user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OAuthProvider provider;
    @Column(name = "provider_subject", nullable = false, length = 255) private String providerSubject;
    @Column(name = "email_at_link", length = 255) private String emailAtLink;
    @Column(name = "created_date_time", nullable = false) private Instant createdDateTime;
    @Column(name = "modified_date_time", nullable = false) private Instant modifiedDateTime;

    protected OAuthIdentity() {}

    public OAuthIdentity(UserAccount user, OAuthProvider provider, String providerSubject, String emailAtLink) {
        var now = Instant.now();
        this.id = UUID.randomUUID();
        this.user = user;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.emailAtLink = emailAtLink;
        this.createdDateTime = now;
        this.modifiedDateTime = now;
    }

    public UUID getId() { return id; }
    public UserAccount getUser() { return user; }
    public OAuthProvider getProvider() { return provider; }
    public String getProviderSubject() { return providerSubject; }
}
