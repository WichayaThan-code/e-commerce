package com.thaishopfun.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "tsf_refresh";
    private final AuthService auth;
    private final SecurityProperties properties;

    public AuthController(AuthService auth, SecurityProperties properties) {
        this.auth = auth;
        this.properties = properties;
    }

    @PostMapping("/register")
    ResponseEntity<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return session(auth.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    ResponseEntity<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return session(auth.login(request), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthDtos.AuthResponse> refresh(HttpServletRequest request) {
        return session(auth.refresh(cookie(request)), HttpStatus.OK);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        auth.logout(cookie(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearCookie().toString()).build();
    }

    @GetMapping("/me")
    AuthDtos.UserResponse me(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt) {
        return auth.me(UUID.fromString(jwt.getSubject()));
    }

    private ResponseEntity<AuthDtos.AuthResponse> session(AuthService.Session session, HttpStatus status) {
        return ResponseEntity.status(status)
            .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString())
            .body(session.response());
    }

    private String cookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies()).filter(c -> REFRESH_COOKIE.equals(c.getName())).map(Cookie::getValue).findFirst().orElse(null);
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true).secure(properties.secureCookies())
            .sameSite("Lax").path("/api/auth").maxAge(Duration.ofDays(properties.refreshTokenDays())).build();
    }

    private ResponseCookie clearCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "").httpOnly(true).secure(properties.secureCookies())
            .sameSite("Lax").path("/api/auth").maxAge(Duration.ZERO).build();
    }
}

