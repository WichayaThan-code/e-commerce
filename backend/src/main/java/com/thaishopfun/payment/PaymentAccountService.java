package com.thaishopfun.payment;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.merchant.MerchantApplication;
import com.thaishopfun.merchant.Owner;
import com.thaishopfun.merchant.SensitiveDataCipher;
import com.thaishopfun.merchant.ShopRepository;
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
    /** Align with merchant registration (8–20 digits); Thai bank accounts are typically 10–15. */
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("[0-9]{8,20}");
    private static final Pattern PROMPTPAY_ID = Pattern.compile("[0-9]{10}|[0-9]{13}|[0-9]{15}");

    private final ShopPaymentGatewayRepository gateways;
    private final SensitiveDataCipher cipher;
    private final ShopRepository shops;

    public PaymentAccountService(ShopPaymentGatewayRepository gateways, SensitiveDataCipher cipher, ShopRepository shops) {
        this.gateways = gateways;
        this.cipher = cipher;
        this.shops = shops;
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
    @Transactional
    public List<PaymentDtos.PaymentAccountView> activeForBuyer(UUID shopId) {
        shops.findById(shopId).ifPresent(shop ->
            ensureBankFromRegistration(shopId, shop.getApplication(), shop.getOwner(), null));
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
            else if (!ACCOUNT_NUMBER.matcher(number).matches()) violations.add(new FieldViolation("accountNumber", "INVALID", "เลขบัญชีต้องเป็นตัวเลข 8-20 หลัก"));
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

    /**
     * First-time BANK_TRANSFER row from merchant registration (application, then owner fallback).
     * No-op when a BANK_TRANSFER gateway already exists or registration bank data is incomplete.
     * PromptPay is left empty for the merchant to add later in settings.
     */
    @Transactional
    public boolean ensureBankFromRegistration(UUID shopId, MerchantApplication application, Owner owner, UUID userId) {
        if (shopId == null) return false;
        if (gateways.findByShopIdAndProvider(shopId, "BANK_TRANSFER").isPresent()) return false;

        String bank = application == null ? null : clean(application.getBankName());
        String accountName = application == null ? null : clean(application.getBankAccountName());
        String encrypted = application == null ? null : application.getBankAccountEncrypted();
        if ((bank == null || accountName == null || encrypted == null) && owner != null) {
            bank = clean(owner.getBankName());
            accountName = clean(owner.getBankAccountName());
            encrypted = owner.getBankAccountEncrypted();
        }

        String number = null;
        try {
            if (encrypted != null) number = digits(cipher.decrypt(encrypted));
        } catch (RuntimeException ignored) {
            return false;
        }
        if (bank == null || accountName == null || number == null || !ACCOUNT_NUMBER.matcher(number).matches()) {
            return false;
        }
        var config = new LinkedHashMap<String, Object>();
        config.put("accountName", accountName);
        config.put("bankName", bank);
        config.put("accountNumber", number);
        var gateway = new ShopPaymentGateway(shopId, "BANK_TRANSFER");
        gateway.setDisplayName("โอนเงินผ่านธนาคาร");
        gateway.setConfigEncrypted(cipher.encrypt(OrderJson.write(config)));
        gateway.setActive(true);
        gateway.stamp(userId);
        gateways.save(gateway);
        return true;
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
