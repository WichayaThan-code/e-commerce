package com.thaishopfun.merchant;

import com.thaishopfun.auth.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "owner_audit_log")
public class OwnerAuditLog {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id") private Owner owner;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private UserAccount user;
    @Column(name = "field_name", nullable = false, length = 50) private String fieldName;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected OwnerAuditLog() {}

    public OwnerAuditLog(Owner owner, UserAccount user, String fieldName) {
        this.id = UUID.randomUUID();
        this.owner = owner;
        this.user = user;
        this.fieldName = fieldName;
        this.createdAt = Instant.now();
    }
}
