package com.thaishopfun.merchant.delivery;

/**
 * Fired when admin approves / rejects / suspends / restores a shop.
 * Payload is plain strings only (safe after commit).
 */
public record ShopStatusChangedMailEvent(
    ShopLifecycleAction action,
    long shopId,
    String shopName,
    String ownerName,
    String ownerEmail,
    String reason
) {}
