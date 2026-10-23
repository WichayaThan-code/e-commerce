package com.thaishopfun.buyer;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class BuyerAddressService {
    private static final int MAX_ADDRESSES = 8;
    private static final Pattern PHONE = Pattern.compile("^0\\d{8,9}$");
    private static final Pattern POSTAL = Pattern.compile("^\\d{5}$");

    private final BuyerAddressRepository addresses;

    public BuyerAddressService(BuyerAddressRepository addresses) {
        this.addresses = addresses;
    }

    @Transactional(readOnly = true)
    public List<BuyerAddressDtos.AddressView> list(UUID buyerUserId) {
        return addresses.findByBuyerUserIdAndDeletedAtIsNullOrderByDefaultAddressDescModifiedDateTimeDescIdAsc(buyerUserId)
                .stream().map(BuyerAddressDtos.AddressView::of).toList();
    }

    @Transactional
    public BuyerAddressDtos.AddressView create(UUID buyerUserId, BuyerAddressDtos.AddressRequest request) {
        validate(request);
        long count = addresses.countByBuyerUserIdAndDeletedAtIsNull(buyerUserId);
        if (count >= MAX_ADDRESSES) {
            throw new AuthException(HttpStatus.UNPROCESSABLE_ENTITY, "ADDRESS_LIMIT",
                    "บันทึกที่อยู่ได้สูงสุด " + MAX_ADDRESSES + " รายการ");
        }
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault()) || count == 0;
        if (makeDefault) addresses.clearDefaults(buyerUserId);

        var row = new BuyerAddress(buyerUserId);
        apply(row, request);
        row.setDefault(makeDefault);
        row.stamp(buyerUserId);
        return BuyerAddressDtos.AddressView.of(addresses.save(row));
    }

    @Transactional
    public BuyerAddressDtos.AddressView update(UUID buyerUserId, UUID id, BuyerAddressDtos.AddressRequest request) {
        validate(request);
        var row = addresses.findByIdAndBuyerUserIdAndDeletedAtIsNull(id, buyerUserId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "ไม่พบที่อยู่นี้"));
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault()) || row.isDefault();
        if (makeDefault) addresses.clearDefaults(buyerUserId);
        apply(row, request);
        row.setDefault(makeDefault);
        row.stamp(buyerUserId);
        return BuyerAddressDtos.AddressView.of(addresses.save(row));
    }

    @Transactional
    public void delete(UUID buyerUserId, UUID id) {
        var row = addresses.findByIdAndBuyerUserIdAndDeletedAtIsNull(id, buyerUserId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "ไม่พบที่อยู่นี้"));
        boolean wasDefault = row.isDefault();
        row.softDelete(Instant.now());
        row.stamp(buyerUserId);
        addresses.save(row);
        if (wasDefault) {
            var rest = addresses.findByBuyerUserIdAndDeletedAtIsNullOrderByDefaultAddressDescModifiedDateTimeDescIdAsc(buyerUserId);
            if (!rest.isEmpty()) {
                var next = rest.get(0);
                next.setDefault(true);
                next.stamp(buyerUserId);
                addresses.save(next);
            }
        }
    }

    private static void apply(BuyerAddress row, BuyerAddressDtos.AddressRequest request) {
        row.apply(
                trimToNull(request.label(), 50),
                request.recipientName().trim(),
                request.phone().replaceAll("[\\s-]", ""),
                request.addressLine1().trim(),
                blankToNull(request.addressLine2()),
                blankToNull(request.subdistrict()),
                request.district().trim(),
                request.province().trim(),
                request.postalCode().trim()
        );
    }

    private static void validate(BuyerAddressDtos.AddressRequest request) {
        if (request == null) {
            throw new ValidationException(List.of(new FieldViolation("body", "REQUIRED", "กรุณาระบุข้อมูลที่อยู่")));
        }
        var violations = new ArrayList<FieldViolation>();
        required(request.recipientName(), "recipientName", "ชื่อผู้รับ", 150, violations);
        required(request.phone(), "phone", "เบอร์โทรผู้รับ", 30, violations);
        required(request.addressLine1(), "addressLine1", "ที่อยู่", 255, violations);
        optional(request.addressLine2(), "addressLine2", 255, violations);
        optional(request.subdistrict(), "subdistrict", 100, violations);
        required(request.district(), "district", "อำเภอ/เขต", 100, violations);
        required(request.province(), "province", "จังหวัด", 100, violations);
        required(request.postalCode(), "postalCode", "รหัสไปรษณีย์", 10, violations);
        optional(request.label(), "label", 50, violations);
        if (request.phone() != null && !request.phone().isBlank()) {
            var phone = request.phone().replaceAll("[\\s-]", "");
            if (!PHONE.matcher(phone).matches()) {
                violations.add(new FieldViolation("phone", "INVALID", "เบอร์โทรไม่ถูกต้อง"));
            }
        }
        if (request.postalCode() != null && !request.postalCode().isBlank()
                && !POSTAL.matcher(request.postalCode().trim()).matches()) {
            violations.add(new FieldViolation("postalCode", "INVALID", "รหัสไปรษณีย์ต้องเป็นตัวเลข 5 หลัก"));
        }
        if (!violations.isEmpty()) throw new ValidationException(violations);
    }

    private static void required(String value, String field, String label, int max, List<FieldViolation> out) {
        if (value == null || value.isBlank()) {
            out.add(new FieldViolation(field, "REQUIRED", "กรุณาระบุ" + label));
        } else if (value.trim().length() > max) {
            out.add(new FieldViolation(field, "TOO_LONG", label + "ยาวเกินไป"));
        }
    }

    private static void optional(String value, String field, int max, List<FieldViolation> out) {
        if (value != null && value.trim().length() > max) {
            out.add(new FieldViolation(field, "TOO_LONG", field + " ยาวเกินไป"));
        }
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        var t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String trimToNull(String value, int max) {
        if (value == null) return null;
        var t = value.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }
}
