package com.thaishopfun.cart;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** All cart money is THB with scale 2, HALF_UP. */
public final class CartMoney {
    public static final String CURRENCY = "THB";
    public static final RoundingMode ROUND = RoundingMode.HALF_UP;
    public static final int SCALE = 2;

    private CartMoney() {}

    public static BigDecimal of(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(SCALE) : value.setScale(SCALE, ROUND);
    }

    public static BigDecimal of(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, ROUND);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE);
    }

    public static BigDecimal multiply(BigDecimal unit, int quantity) {
        return of(of(unit).multiply(BigDecimal.valueOf(quantity)));
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return of(a).min(of(b));
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return of(a).max(of(b));
    }
}
