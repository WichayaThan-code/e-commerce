package com.thaishopfun.customer;

/**
 * shop_customers.status (CHECK ck_shop_customers_status; widened in V22). ARCHIVED = removed from normal lists
 * (soft delete via DELETE endpoint); MERGED = folded into another customer (merged_into_id), set only by merge.
 */
public enum CustomerStatus {
    ACTIVE, INACTIVE, BLOCKED, ARCHIVED, MERGED
}
