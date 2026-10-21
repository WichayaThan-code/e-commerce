package com.thaishopfun.order;

/**
 * How the buyer pays an order (orders.payment_method, V29). BANK_TRANSFER = pay first by transfer / PromptPay slip
 * (V1 payment-first flow); COD = cash on delivery, collected by the shop / carrier after delivery.
 */
public enum PaymentMethod {
    BANK_TRANSFER, COD;

    /** null / blank = BANK_TRANSFER (default); unknown value = null. */
    public static PaymentMethod parse(String raw) {
        if (raw == null || raw.isBlank()) return BANK_TRANSFER;
        try {
            return PaymentMethod.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
