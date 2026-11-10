package com.thaishopfun.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, UUID> {
    Optional<OAuthIdentity> findByProviderAndProviderSubject(OAuthProvider provider, String providerSubject);
    boolean existsByUserIdAndProvider(UUID userId, OAuthProvider provider);
}
