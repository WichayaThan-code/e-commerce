package com.thaishopfun.auth;

/**
 * Claims extracted after a successful IdP token verification.
 *
 * @param subject stable provider user id (JWT {@code sub} / Facebook id)
 * @param email lowercase email when the provider supplied one
 * @param emailVerified whether the IdP asserts the email is verified
 * @param firstName best-effort given name (may be blank)
 * @param lastName best-effort family name (may be blank)
 */
public record OAuthVerifiedIdentity(
    String subject,
    String email,
    boolean emailVerified,
    String firstName,
    String lastName
) {}
