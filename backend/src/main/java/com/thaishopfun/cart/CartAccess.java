package com.thaishopfun.cart;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.RequestUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Resolves the cart owner for a request.
 * <ul>
 *   <li>Buyer endpoints: JWT required (Spring Security already enforces {@code @RequirePermission}).</li>
 *   <li>Guest endpoints: {@code X-Cart-Token} required; no JWT.</li>
 *   <li>Merge: JWT + {@code X-Cart-Token} (guest lines move into the buyer's carts).</li>
 * </ul>
 */
@Component
public class CartAccess {
    public static final String CART_TOKEN_HEADER = "X-Cart-Token";
    private static final SecureRandom RANDOM = new SecureRandom();

    public CartOwner requireBuyer() {
        var userId = currentUserId();
        if (userId == null) throw new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "กรุณาเข้าสู่ระบบ");
        return new CartOwner(userId, null, null);
    }

    public CartOwner requireGuest(HttpServletRequest request) {
        var token = rawToken(request);
        if (token == null || token.isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "CART_TOKEN_REQUIRED", "ต้องส่ง X-Cart-Token");
        }
        validateTokenFormat(token);
        return new CartOwner(null, token, hash(token));
    }

    /** Optional guest token for merge; returns null when the header is absent. */
    public String optionalGuestToken(HttpServletRequest request) {
        var token = rawToken(request);
        if (token == null || token.isBlank()) return null;
        validateTokenFormat(token);
        return token;
    }

    public String newGuestToken() {
        var bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public String hash(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String rawToken(HttpServletRequest request) {
        return request.getHeader(CART_TOKEN_HEADER);
    }

    private static void validateTokenFormat(String token) {
        if (token.length() < 16 || token.length() > 128 || !token.chars().allMatch(c ->
            (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "CART_TOKEN_INVALID", "X-Cart-Token ไม่ถูกต้อง");
        }
    }

    private static UUID currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return RequestUser.id(jwt);
        }
        return null;
    }
}
