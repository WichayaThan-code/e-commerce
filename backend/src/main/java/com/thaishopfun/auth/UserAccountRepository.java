package com.thaishopfun.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmailIgnoreCaseOrPhone(String email, String phone);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);
}

