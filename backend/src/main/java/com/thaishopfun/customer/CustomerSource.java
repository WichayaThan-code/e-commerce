package com.thaishopfun.customer;

/** Where a shop customer came from (shop_customers.source, CHECK ck_shop_customers_source; widened in V22). */
public enum CustomerSource {
    STOREFRONT, MANUAL, OMS, IMPORT, LINE, FACEBOOK, INSTAGRAM, SHOPEE, LAZADA, TIKTOK, WALK_IN, WEBSITE, REFERRAL, OTHER
}
