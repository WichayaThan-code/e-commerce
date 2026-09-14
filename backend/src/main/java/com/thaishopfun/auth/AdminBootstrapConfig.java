package com.thaishopfun.auth;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrapConfig {
    @Bean
    @ConditionalOnProperty(name = "app.bootstrap-admin.password")
    ApplicationRunner bootstrapAdmin(
        UserAccountRepository users,
        PasswordEncoder passwords,
        @Value("${app.bootstrap-admin.username:admin}") String username,
        @Value("${app.bootstrap-admin.password}") String password
    ) {
        return args -> {
            var normalized = username.trim().toLowerCase();
            if (users.findByEmailIgnoreCaseOrPhone(normalized, normalized).isEmpty()) {
                users.save(UserAccount.admin(normalized, passwords.encode(password)));
            }
        };
    }
}
