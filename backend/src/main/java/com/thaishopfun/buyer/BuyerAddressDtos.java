package com.thaishopfun.buyer;

import java.time.Instant;
import java.util.UUID;

public final class BuyerAddressDtos {
    private BuyerAddressDtos() {}

    public record AddressRequest(
            String label,
            String recipientName,
            String phone,
            String addressLine1,
            String addressLine2,
            String subdistrict,
            String district,
            String province,
            String postalCode,
            Boolean isDefault
    ) {}

    public record AddressView(
            UUID id,
            String label,
            String recipientName,
            String phone,
            String addressLine1,
            String addressLine2,
            String subdistrict,
            String district,
            String province,
            String postalCode,
            String countryCode,
            boolean isDefault,
            Instant updatedAt
    ) {
        static AddressView of(BuyerAddress a) {
            return new AddressView(
                    a.getId(),
                    a.getLabel(),
                    a.getRecipientName(),
                    a.getPhone(),
                    a.getAddressLine1(),
                    a.getAddressLine2(),
                    a.getSubdistrict(),
                    a.getDistrict(),
                    a.getProvince(),
                    a.getPostalCode(),
                    a.getCountryCode(),
                    a.isDefault(),
                    a.getModifiedDateTime()
            );
        }
    }
}
