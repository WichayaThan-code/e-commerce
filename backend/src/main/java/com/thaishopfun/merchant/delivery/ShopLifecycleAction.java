package com.thaishopfun.merchant.delivery;

/** Shop status transitions that email the shop owner. */
public enum ShopLifecycleAction {
    APPROVED,
    REJECTED,
    SUSPENDED,
    RESTORED
}
