package com.thaishopfun.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmailIgnoreCaseOrPhone(String email, String phone);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);

    /** Single PK lookup used on every authenticated request to validate the JWT "pv" claim. */
    @Query("""
        SELECT u.permissionVersion FROM UserAccount u
        WHERE u.id = :id AND u.status = com.thaishopfun.auth.UserStatus.ACTIVE AND u.deletedAt IS NULL
        """)
    Optional<Integer> findActivePermissionVersion(@Param("id") UUID id);
}
