package com.thaishopfun.auth;

import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrapConfig {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapConfig.class);

    /**
     * Creates the bootstrap admin account (app.bootstrap-admin.*) and grants it an active PLATFORM_ADMIN row in
     * platform_roles, the only source of admin rights. An account that already exists is never promoted here
     * (it could be an ordinary user with the same e-mail, or an admin whose role was deliberately revoked);
     * grant it through platform_roles instead.
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
            var existing = users.findByEmailIgnoreCaseOrPhone(normalized, normalized);
            if (existing.isPresent()) {
                if (!platformRoles.existsByUserIdAndRevokedAtIsNull(existing.get().getId())) {
                    log.warn("Bootstrap admin account {} exists without an active platform role; not granting one automatically "
                        + "(insert a platform_roles row to make it an admin)", existing.get().getId());
                }
                return;
            }
            var account = users.save(UserAccount.systemAccount(normalized, passwords.encode(password)));
            platformRoles.save(PlatformRole.grant(account.getId(), PlatformRole.PLATFORM_ADMIN, null, "bootstrap-admin"));
        };
    }
}
