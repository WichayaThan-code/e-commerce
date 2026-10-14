package com.thaishopfun.auth;

import com.thaishopfun.auth.authz.AuthorizationInterceptor;
import com.thaishopfun.auth.authz.AuthorizationService;
import com.thaishopfun.auth.authz.JsonSecurityErrorHandlers;
import com.thaishopfun.auth.authz.PublicEndpointRegistry;
import jakarta.servlet.DispatcherType;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    /**
     * Login lookup. Authorities are the user's role codes from the role tables (platform_roles + active shop
     * memberships, as ROLE_&lt;code&gt;); users.role no longer exists. Permission checks never use these authorities.
     */
    @Bean UserDetailsService userDetailsService(UserAccountRepository users, AuthorizationService authorization) {
        return identity -> {
            var normalized = identity.trim().toLowerCase();
            var account = users.findByEmailIgnoreCaseOrPhone(normalized, identity.trim())
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("ไม่พบบัญชีผู้ใช้"));
            return User.withUsername(account.getId().toString())
                .password(account.getPasswordHash())
                .roles(authorization.roleCodes(account.getId()).toArray(String[]::new))
                .disabled(account.getStatus() != UserStatus.ACTIVE || account.isDeleted())
                .build();
        };
    }

    @Bean DaoAuthenticationProvider authenticationProvider(UserDetailsService users, PasswordEncoder passwords) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwords);
        return provider;
    }

    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean JwtEncoder jwtEncoder(SecurityProperties properties) {
        var key = new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean JwtDecoder jwtDecoder(SecurityProperties properties, UserAccountRepository users) {
        var key = new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), permissionVersionValidator(users)));
        return decoder;
    }

    /**
     * Access tokens carry the user's permission_version as "pv". Password reset/change (and any future
     * revoke/suspend) bumps users.permission_version, so older access tokens stop working immediately.
     * Cost: one primary-key lookup per authenticated request. Suspended/deleted users are rejected too.
     */
    static OAuth2TokenValidator<Jwt> permissionVersionValidator(UserAccountRepository users) {
        var invalid = OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Access token is no longer valid", null));
        return jwt -> {
            UUID userId;
            try {
                userId = UUID.fromString(jwt.getSubject());
            } catch (RuntimeException exception) {
                return invalid;
            }
            Object claim = jwt.getClaims().get("pv");
            if (!(claim instanceof Number tokenVersion)) return invalid;
            return users.findActivePermissionVersion(userId)
                .filter(current -> current == tokenVersion.intValue())
                .map(current -> OAuth2TokenValidatorResult.success())
                .orElse(invalid);
        };
    }

    @Bean JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * Layer 1 (this filter chain): endpoints annotated {@code @PublicEndpoint} are permitAll, every other request
     * needs a valid access token (401 JSON otherwise). Layer 2 ({@link AuthorizationInterceptor}): the permission
     * declared on the endpoint is checked against the role/permission matrix in the database (403 / 404).
     * The informational "roles" claim (role codes from platform_roles / shop_members) is not used for authorization.
     */
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter, PublicEndpointRegistry publicEndpoints) throws Exception {
        var entryPoint = JsonSecurityErrorHandlers.authenticationEntryPoint();
        var accessDenied = JsonSecurityErrorHandlers.accessDeniedHandler();
        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers(publicEndpoints).permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDenied))
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDenied))
            .build();
    }

    @Bean CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(properties.allowedOrigin()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
