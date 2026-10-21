package com.thaishopfun.order;

import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.merchant.SensitiveDataCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Per-shop Cash on Delivery setting (V29): one V1 shop_payment_gateways row with provider COD. is_active = COD offered
 * at checkout; config_encrypted = {"fee": "20.00", "maxOrderAmount": "3000.00" | null} (SensitiveDataCipher, like the
 * receiving accounts). No row = COD disabled. The fee is added to the order total; maxOrderAmount caps the total incl. fee.
 */
@Service
public class CodSettingsService {
    public static final String PROVIDER = "COD";
    public static final BigDecimal MAX_FEE = new BigDecimal("9999.99");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    public record Settings(boolean enabled, BigDecimal fee, BigDecimal maxOrderAmount, Instant updatedAt) {
        static final Settings DISABLED = new Settings(false, BigDecimal.ZERO.setScale(2), null, null);
    }

    private final ShopPaymentGatewayRepository gateways;
    private final SensitiveDataCipher cipher;

    public CodSettingsService(ShopPaymentGatewayRepository gateways, SensitiveDataCipher cipher) {
        this.gateways = gateways;
        this.cipher = cipher;
    }

    @Transactional(readOnly = true)
    public Settings settings(UUID shopId) {
        return gateways.findByShopIdAndProvider(shopId, PROVIDER).map(this::read).orElse(Settings.DISABLED);
    }

    @Transactional
    public Settings update(UUID shopId, Boolean enabled, BigDecimal rawFee, BigDecimal rawMax, UUID userId) {
        var violations = new ArrayList<FieldViolation>();
        if (enabled == null) violations.add(new FieldViolation("enabled", "REQUIRED", "กรุณาระบุว่าเปิดหรือปิด COD"));
        BigDecimal fee = rawFee == null ? BigDecimal.ZERO : rawFee;
        if (fee.signum() < 0 || fee.compareTo(MAX_FEE) > 0 || fee.stripTrailingZeros().scale() > 2) {
            violations.add(new FieldViolation("fee", "INVALID", "ค่าธรรมเนียม COD ต้องอยู่ระหว่าง 0 ถึง " + MAX_FEE + " และมีทศนิยมไม่เกิน 2 ตำแหน่ง"));
        }
        if (rawMax != null && (rawMax.signum() <= 0 || rawMax.compareTo(MAX_AMOUNT) > 0 || rawMax.stripTrailingZeros().scale() > 2)) {
            violations.add(new FieldViolation("maxOrderAmount", "INVALID", "ยอดสูงสุดต่อออเดอร์ต้องมากกว่า 0 และมีทศนิยมไม่เกิน 2 ตำแหน่ง (หรือเว้นว่าง = ไม่จำกัด)"));
        }
        if (!violations.isEmpty()) throw new ValidationException(violations);
        var config = new LinkedHashMap<String, Object>();
        config.put("fee", fee.setScale(2, RoundingMode.UNNECESSARY).toPlainString());
        config.put("maxOrderAmount", rawMax == null ? null : rawMax.setScale(2, RoundingMode.UNNECESSARY).toPlainString());
        var gateway = gateways.findByShopIdAndProvider(shopId, PROVIDER).orElseGet(() -> new ShopPaymentGateway(shopId, PROVIDER));
        gateway.setDisplayName("เก็บเงินปลายทาง (COD)");
        gateway.setConfigEncrypted(cipher.encrypt(OrderJson.write(config)));
        gateway.setActive(enabled);
        gateway.stamp(userId);
        return read(gateways.saveAndFlush(gateway));
    }

    private Settings read(ShopPaymentGateway gateway) {
        BigDecimal fee = BigDecimal.ZERO.setScale(2);
        BigDecimal max = null;
        try {
            var config = gateway.getConfigEncrypted() == null ? null : OrderJson.read(cipher.decrypt(gateway.getConfigEncrypted()));
            if (config != null) {
                if (config.get("fee") != null) fee = new BigDecimal(config.get("fee").toString()).setScale(2, RoundingMode.HALF_UP);
                if (config.get("maxOrderAmount") != null) max = new BigDecimal(config.get("maxOrderAmount").toString()).setScale(2, RoundingMode.HALF_UP);
            }
        } catch (RuntimeException exception) {
            // unreadable config (secret rotated): do not offer COD with unknown fee / limit
            return new Settings(false, fee, null, gateway.getModifiedDateTime());
        }
        return new Settings(gateway.isActive(), fee, max, gateway.getModifiedDateTime());
    }
}
