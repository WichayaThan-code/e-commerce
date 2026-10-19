package com.thaishopfun.cart;

import java.util.UUID;

/**
 * Who owns the cart for this request. Exactly one of buyerUserId / guestToken is set.
 * guestTokenHash is SHA-256 hex of the raw X-Cart-Token (the raw token is never stored).
 */
public record CartOwner(UUID buyerUserId, String guestToken, String guestTokenHash) {
    public boolean isGuest() { return buyerUserId == null; }
    public boolean isBuyer() { return buyerUserId != null; }
}
