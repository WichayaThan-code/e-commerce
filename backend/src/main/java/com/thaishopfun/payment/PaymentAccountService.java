package com.thaishopfun.payment;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.merchant.SensitiveDataCipher;
import com.thaishopfun.order.OrderJson;
import com.thaishopfun.order.ShopPaymentGateway;
import com.thaishopfun.order.ShopPaymentGatewayRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The shop's own receiving accounts for direct transfer, stored in V1 shop_payment_gateways (one row per provider):
 * BANK_TRANSFER = bank + account number + account name, PROMPTPAY = PromptPay id (phone / citizen / tax id) + name.
 * Account details are encrypted at rest (config_encrypted, SensitiveDataCipher) and never logged.
 */
@Service
public class PaymentAccountService {
    public static final List<String> MANUAL_PROVIDERS = List.of("BANK_TRANSFER", "PROMPTPAY");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("[0-9]{10,15}");
    private static final Pattern PROMPTPAY_ID = Pattern.compile("[0-9]{10}|[0-9]{13}|[0-9]{15}");

    private final ShopPaymentGatewayRepository gateways;
    private final SensitiveDataCipher cipher;

    public PaymentAccountService(ShopPaymentGatewayRepository gateways, SensitiveDataCipher cipher) {
        this.gateways = gateways;
        this.cipher = cipher;
    }

    public static String provider(String raw) {
        var provider = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (!MANUAL_PROVIDERS.contains(provider)) {
            throw new AuthException(HttpStatus.NOT_FOUND, "PAYMENT_PROVIDER_NOT_SUPPORTED", "รองรับเฉพาะ BANK_TRANSFER หรือ PROMPTPAY");
        }
        return provider;
    }

    @Transactional(readOnly = true)
    public List<PaymentDtos.PaymentAccountView> list(UUID shopId, boolean masked) {
        return gateways.findByShopIdOrderByProviderAsc(shopId).stream().filter(g -> MANUAL_PROVIDERS.contains(g.getProvider()))
            .map(g -> view(g, masked)).toList();
    }

    /** What a buyer may see for an order awaiting payment: active manual accounts, unmasked. */
    @Transactional(readOnly = true)
    public List<PaymentDtos.PaymentAccountView> activeForBuyer(UUID shopId) {
        return gateways.findByShopIdAndProviderInAndActiveTrue(shopId, MANUAL_PROVIDERS).stream()
            .sorted(java.util.Comparator.comparing(ShopPaymentGateway::getProvider)).map(g -> view(g, false)).toList();
    }

    public List<String> activeProviders(UUID shopId) {
        return gateways.findByShopIdAndProviderInAndActiveTrue(shopId, MANUAL_PROVIDERS).stream().map(ShopPaymentGateway::getProvider).sorted().toList();
    }

    @Transactional
    public PaymentDtos.PaymentAccountView upsert(UUID shopId, String rawProvider, PaymentDtos.PaymentAccountRequest body, UUID userId) {
        var provider = provider(rawProvider);
        var req = body == null ? new PaymentDtos.PaymentAccountRequest(null, null, null, null, null, null) : body;
        var violations = new ArrayList<FieldViolation>();
        var config = new LinkedHashMap<String, Object>();
        var accountName = clean(req.accountName());
        if (accountName == null) violations.add(new FieldViolation("accountName", "REQUIRED", "กรุณาระบุชื่อบัญชี"));
        else if (accountName.length() > 150) violations.add(new FieldViolation("accountName", "MAX_LENGTH", "ชื่อบัญชียาวเกิน 150 ตัวอักษร"));
        config.put("accountName", accountName);
        if (provider.equals("BANK_TRANSFER")) {
            var bank = clean(req.bankName());
            var number = digits(req.accountNumber());
            if (bank == null) violations.add(new FieldViolation("bankName", "REQUIRED", "กรุณาระบุธนาคาร"));
            else if (bank.length() > 100) violations.add(new FieldViolation("bankName", "MAX_LENGTH", "ชื่อธนาคารยาวเกิน 100 ตัวอักษร"));
            if (number == null) violations.add(new FieldViolation("accountNumber", "REQUIRED", "กรุณาระบุเลขบัญชี"));
            else if (!ACCOUNT_NUMBER.matcher(number).matches()) violations.add(new FieldViolation("accountNumber", "INVALID", "เลขบัญชีต้องเป็นตัวเลข 10-15 หลัก"));
            config.put("bankName", bank);
            config.put("accountNumber", number);
        } else {
            var id = digits(req.promptPayId());
            if (id == null) violations.add(new FieldViolation("promptPayId", "REQUIRED", "กรุณาระบุพร้อมเพย์"));
            else if (!PROMPTPAY_ID.matcher(id).matches()) violations.add(new FieldViolation("promptPayId", "INVALID", "พร้อมเพย์ต้องเป็นเบอร์มือถือ 10 หลัก เลขบัตร/ภาษี 13 หลัก หรือ e-Wallet 15 หลัก"));
            config.put("promptPayId", id);
        }
        var display = clean(req.displayName());
        if (display != null && display.length() > 100) violations.add(new FieldViolation("displayName", "MAX_LENGTH", "ชื่อที่แสดงยาวเกิน 100 ตัวอักษร"));
        if (!violations.isEmpty()) throw new ValidationException(violations);
        var gateway = gateways.findByShopIdAndProvider(shopId, provider).orElseGet(() -> new ShopPaymentGateway(shopId, provider));
        gateway.setDisplayName(display != null ? display : provider.equals("BANK_TRANSFER") ? "โอนเงินผ่านธนาคาร" : "พร้อมเพย์");
        gateway.setConfigEncrypted(cipher.encrypt(OrderJson.write(config)));
        gateway.setActive(req.active() == null || req.active());
        gateway.stamp(userId);
        return view(gateways.save(gateway), false);
    }

    @Transactional
    public PaymentDtos.PaymentAccountView deactivate(UUID shopId, String rawProvider, UUID userId) {
        var provider = provider(rawProvider);
        var gateway = gateways.findByShopIdAndProvider(shopId, provider)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "PAYMENT_ACCOUNT_NOT_FOUND", "ยังไม่ได้ตั้งค่าบัญชีรับเงินนี้"));
        gateway.setActive(false);
        gateway.stamp(userId);
        return view(gateway, false);
    }

    private PaymentDtos.PaymentAccountView view(ShopPaymentGateway gateway, boolean masked) {
        Map<String, Object> config;
        try {
            config = gateway.getConfigEncrypted() == null ? Map.of() : OrderJson.read(cipher.decrypt(gateway.getConfigEncrypted()));
        } catch (RuntimeException exception) {
            config = Map.of(); // unreadable config (secret rotated): show the row without details
        }
        if (config == null) config = Map.of();
        var number = (String) config.get("accountNumber");
        var promptPay = (String) config.get("promptPayId");
        return new PaymentDtos.PaymentAccountView(gateway.getProvider(), gateway.getDisplayName(), (String) config.get("bankName"),
            masked ? mask(number) : number, (String) config.get("accountName"), masked ? mask(promptPay) : promptPay, gateway.isActive(), masked);
    }

    public static String mask(String value) {
        if (value == null) return null;
        return value.length() <= 4 ? "****" : "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }

    private static String digits(String raw) {
        var value = clean(raw);
        return value == null ? null : value.replaceAll("[\\s-]", "");
    }

    static String clean(String value) {
        if (value == null) return null;
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
