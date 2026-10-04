CREATE UNIQUE INDEX uq_merchant_applications_shop_name_lower
    ON merchant_applications (lower(shop_name));
