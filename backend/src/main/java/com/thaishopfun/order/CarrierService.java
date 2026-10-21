package com.thaishopfun.order;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Carrier master list (V30 carriers), per-shop enablement (shop_carriers) and tracking-number rules:
 * normalized = trimmed, spaces removed, upper case; format = carriers.tracking_pattern when set, otherwise
 * {@link #GENERIC_TRACKING}; unique per carrier among live (not CANCELLED) shipments.
 */
@Service
public class CarrierService {
    public static final Pattern GENERIC_TRACKING = Pattern.compile("^[A-Z0-9][A-Z0-9-]{3,39}$");
    private static final Map<String, String> ALIASES = Map.ofEntries(
        Map.entry("J&T", "JT"), Map.entry("J_T", "JT"), Map.entry("JNT", "JT"), Map.entry("J&T_EXPRESS", "JT"),
        Map.entry("KEX", "KERRY"), Map.entry("KERRY_EXPRESS", "KERRY"),
        Map.entry("FLASH_EXPRESS", "FLASH"),
        Map.entry("THAILAND_POST", "THAIPOST"), Map.entry("THAILANDPOST", "THAIPOST"), Map.entry("THAI_POST", "THAIPOST"),
        Map.entry("NINJA_VAN", "NINJAVAN"), Map.entry("SHOPEE_EXPRESS", "SPX"));

    public record Checked(Carrier carrier, String trackingNumber) {}

    private final CarrierRepository carriers;
    private final ShopCarrierRepository shopCarriers;
    private final ShipmentRepository shipments;
    private final com.thaishopfun.cart.ShippingMethodRepository methods;

    public CarrierService(CarrierRepository carriers, ShopCarrierRepository shopCarriers, ShipmentRepository shipments,
                          com.thaishopfun.cart.ShippingMethodRepository methods) {
        this.methods = methods;
        this.carriers = carriers;
        this.shopCarriers = shopCarriers;
        this.shipments = shipments;
    }

    public static String normalizeCode(String raw) {
        if (raw == null || raw.isBlank()) return null;
        var code = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        return ALIASES.getOrDefault(code, code);
    }

    public static String normalizeTracking(String raw) {
        if (raw == null) return null;
        var value = raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }

    /**
     * Validates carrier (exists, active, enabled for the shop) and tracking number format. Problems are added to
     * violations (field carrierCode / trackingNumber); returns null then. Does not check uniqueness.
     */
    @Transactional(readOnly = true)
    public Checked check(UUID shopId, String rawCarrier, String rawTracking, List<FieldViolation> violations) {
        int before = violations.size();
        var code = normalizeCode(rawCarrier);
        Carrier carrier = null;
        if (code == null) {
            violations.add(new FieldViolation("carrierCode", "REQUIRED", "กรุณาระบุบริษัทขนส่ง"));
        } else {
            carrier = carriers.findById(code).orElse(null);
            if (carrier == null || !carrier.isActive()) {
                violations.add(new FieldViolation("carrierCode", "INVALID", "ไม่รู้จักบริษัทขนส่ง " + code + " (ดู GET /api/carriers)"));
                carrier = null;
            } else if (!enabledForShop(shopId, code)) {
                violations.add(new FieldViolation("carrierCode", "CARRIER_NOT_ENABLED", "ร้านยังไม่ได้เปิดใช้ขนส่ง " + code));
            }
        }
        var tracking = normalizeTracking(rawTracking);
        if (tracking == null) {
            violations.add(new FieldViolation("trackingNumber", "REQUIRED", "กรุณาระบุเลขพัสดุ"));
        } else if (tracking.length() > 100) {
            violations.add(new FieldViolation("trackingNumber", "MAX_LENGTH", "เลขพัสดุยาวเกิน 100 ตัวอักษร"));
        } else if (carrier != null && !formatOk(carrier, tracking)) {
            violations.add(new FieldViolation("trackingNumber", "INVALID_FORMAT", carrier.getTrackingPattern() == null
                ? "เลขพัสดุต้องเป็นตัวอักษร A-Z ตัวเลข หรือ - ยาว 4-40 ตัว"
                : "รูปแบบเลขพัสดุ " + carrier.getNameTh() + " ไม่ถูกต้อง (" + carrier.getTrackingPattern() + ")"));
        }
        return violations.size() == before ? new Checked(carrier, tracking) : null;
    }

    /**
     * 409 TRACKING_NUMBER_DUPLICATE when another live shipment already has this carrier + tracking number. Locks the
     * carrier row first so two concurrent assignments of the same number are serialized (call inside the write tx).
     */
    public void requireUnique(Checked checked, UUID ownShipmentId) {
        carriers.lockByCode(checked.carrier().getCode());
        boolean taken = shipments.findLiveByTracking(checked.carrier().getCode(), checked.trackingNumber()).stream()
            .anyMatch(s -> ownShipmentId == null || !s.getId().equals(ownShipmentId));
        if (taken) {
            throw new AuthException(HttpStatus.CONFLICT, "TRACKING_NUMBER_DUPLICATE",
                "เลขพัสดุ " + checked.trackingNumber() + " ของ " + checked.carrier().getCode() + " ถูกใช้กับการจัดส่งอื่นแล้ว");
        }
    }

    static boolean formatOk(Carrier carrier, String tracking) {
        if (!GENERIC_TRACKING.matcher(tracking).matches() && carrier.getTrackingPattern() == null) return false;
        if (carrier.getTrackingPattern() == null) return true;
        try {
            return Pattern.compile(carrier.getTrackingPattern()).matcher(tracking).matches();
        } catch (PatternSyntaxException exception) {
            return GENERIC_TRACKING.matcher(tracking).matches(); // bad pattern in data: fall back to the generic rule
        }
    }

    /** Shops that never chose carriers may use every active carrier; once they have rows only active rows count. */
    public boolean enabledForShop(UUID shopId, String code) {
        var rows = shopCarriers.findByShopId(shopId);
        if (rows.isEmpty()) return true;
        return rows.stream().anyMatch(r -> r.isActive() && r.getCarrierCode().equals(code));
    }

    /** Carrier of the order's shipping method (default when ship omits carrierCode), or null. */
    public String methodCarrier(UUID shippingMethodId) {
        if (shippingMethodId == null) return null;
        return methods.findById(shippingMethodId).map(m -> normalizeCode(m.getCarrierCode())).orElse(null);
    }

    /** For shipping_methods.carrier_code (optional): must be a known carrier. Returns the normalized code. */
    public String validateMethodCarrier(String raw, List<FieldViolation> violations) {
        var code = normalizeCode(raw);
        if (code == null) return null;
        if (code.length() > 50) {
            violations.add(new FieldViolation("carrierCode", "MAX_LENGTH", "รหัสขนส่งยาวเกิน 50 ตัวอักษร"));
            return null;
        }
        var carrier = carriers.findById(code).orElse(null);
        if (carrier == null || !carrier.isActive()) {
            violations.add(new FieldViolation("carrierCode", "INVALID", "ไม่รู้จักบริษัทขนส่ง " + code + " (ดู GET /api/carriers)"));
        }
        return code;
    }

    @Transactional(readOnly = true)
    public List<ShipmentDtos.CarrierView> list(boolean activeOnly) {
        return carriers.findAllByOrderBySortOrderAscCodeAsc().stream().filter(c -> !activeOnly || c.isActive())
            .map(c -> new ShipmentDtos.CarrierView(c.getCode(), c.getNameTh(), c.getNameEn(), c.getTrackingPattern(), c.isActive(), c.getSortOrder()))
            .toList();
    }

    @Transactional(readOnly = true)
    public ShipmentDtos.ShopCarriersResponse forShop(UUID shopId) {
        var rows = shopCarriers.findByShopId(shopId);
        boolean restricted = !rows.isEmpty();
        var items = carriers.findAllByOrderBySortOrderAscCodeAsc().stream()
            .map(c -> new ShipmentDtos.ShopCarrierView(c.getCode(), c.getNameTh(), c.getNameEn(), c.getTrackingPattern(), c.isActive(),
                c.isActive() && (!restricted || rows.stream().anyMatch(r -> r.isActive() && r.getCarrierCode().equals(c.getCode())))))
            .toList();
        return new ShipmentDtos.ShopCarriersResponse(restricted, items);
    }

    /**
     * Enable / disable one carrier for a shop. The first change on a shop that never chose carriers turns the choice on:
     * enabling X means "only X" from then on; disabling X keeps every other active carrier enabled.
     */
    @Transactional
    public ShipmentDtos.ShopCarriersResponse setEnabled(UUID shopId, String rawCode, Boolean enabled, UUID userId) {
        if (enabled == null) throw new ValidationException(List.of(new FieldViolation("enabled", "REQUIRED", "กรุณาระบุ enabled")));
        var code = normalizeCode(rawCode);
        var carrier = code == null ? null : carriers.findById(code).orElse(null);
        if (carrier == null) throw new AuthException(HttpStatus.NOT_FOUND, "CARRIER_NOT_FOUND", "ไม่พบบริษัทขนส่ง");
        if (enabled && !carrier.isActive()) {
            throw new AuthException(HttpStatus.CONFLICT, "CARRIER_INACTIVE", "บริษัทขนส่งนี้ถูกปิดใช้งานโดยแพลตฟอร์ม");
        }
        var rows = shopCarriers.findByShopId(shopId);
        if (rows.isEmpty() && !enabled) {
            // first choice is a "disable": materialize the implicit "all active" set minus this carrier
            for (var c : carriers.findAllByOrderBySortOrderAscCodeAsc()) {
                if (!c.isActive()) continue;
                var row = new ShopCarrier(shopId, c.getCode());
                row.setActive(!c.getCode().equals(code));
                row.stamp(userId);
                shopCarriers.save(row);
            }
            return forShop(shopId);
        }
        var row = shopCarriers.findByShopIdAndCarrierCode(shopId, code).orElseGet(() -> new ShopCarrier(shopId, code));
        row.setActive(enabled);
        row.stamp(userId);
        shopCarriers.save(row);
        return forShop(shopId);
    }
}
