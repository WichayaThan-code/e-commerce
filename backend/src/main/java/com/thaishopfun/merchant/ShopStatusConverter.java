package com.thaishopfun.merchant;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * The API keeps the shop statuses the UI already uses (PENDING_REVIEW, APPROVED, REJECTED, SUSPENDED).
 * In the V1 schema an operating shop is stored as ACTIVE, so APPROVED is persisted as ACTIVE;
 * the other values are stored as-is (V2 widens ck_shops_status accordingly).
 */
@Converter
public class ShopStatusConverter implements AttributeConverter<ShopStatus, String> {
    @Override
    public String convertToDatabaseColumn(ShopStatus status) {
        if (status == null) return null;
        return status == ShopStatus.APPROVED ? "ACTIVE" : status.name();
    }

    @Override
    public ShopStatus convertToEntityAttribute(String value) {
        if (value == null) return null;
        return switch (value) {
            case "ACTIVE" -> ShopStatus.APPROVED;
            case "CLOSED" -> ShopStatus.REJECTED;
            default -> ShopStatus.valueOf(value);
        };
    }
}
