package com.thaishopfun.auth;

import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrapConfig {
    /**
     * Creates the bootstrap admin account (app.bootstrap-admin.*) and makes sure it holds an active PLATFORM_ADMIN
     * row in platform_roles: platform permissions come from platform_roles + auth_role_permissions, not users.role.
     */
    @Bean
    @ConditionalOnProperty(name = "app.bootstrap-admin.password")
    ApplicationRunner bootstrapAdmin(
        UserAccountRepository users,
        PlatformRoleRepository platformRoles,
        PasswordEncoder passwords,
        @Value("${app.bootstrap-admin.username:admin}") String username,
        @Value("${app.bootstrap-admin.password}") String password
    ) {
        return args -> {
            var normalized = username.trim().toLowerCase();
            var account = users.findByEmailIgnoreCaseOrPhone(normalized, normalized)
                .orElseGet(() -> users.save(UserAccount.admin(normalized, passwords.encode(password))));
            if (account.getRole() == UserRole.ADMIN && !platformRoles.existsByUserIdAndRevokedAtIsNull(account.getId())) {
                platformRoles.save(PlatformRole.grant(account.getId(), PlatformRole.PLATFORM_ADMIN, null, "bootstrap-admin"));
            }
        };
    }
}
