package com.thaishopfun.merchant.delivery;

/**
 * Fired in the same TX as application submit; email delivery runs AFTER_COMMIT on the notification executor.
 * All strings are copied before publish so lazy associations are not touched after commit.
 */
public record ShopApplicationSubmittedMailEvent(
    long shopId,
    String shopName,
    String applicantName,
    String applicantEmail,
    String applicantUserLabel
) {}
