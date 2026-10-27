package com.thaishopfun.customer;

import com.thaishopfun.merchant.MerchantProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Opaque customer-insight keys (AES-256-GCM, deterministic IV) bound to shop.
 * Format: {@code c1} + base64url(iv[12] || ciphertext[17] || tag[16]).
 * Shared by customer-insights and merchant order {@code customer=} filter.
 */
@Component
public class CustomerInsightKeys {
    private static final String PREFIX = "c1";
    private static final byte KIND_CUSTOMER = 'C';
    private static final byte KIND_BUYER = 'U';
    private static final int IV_LEN = 12;
    private static final int PLAIN_LEN = 17;
    private static final int TAG_LEN = 16;
    private static final int BLOB_LEN = IV_LEN + PLAIN_LEN + TAG_LEN;
    private static final String AAD_PREFIX = "tsf-ck1:";
    private static final String MASTER_INFO = "tsf/customer-key/v1";

    /** Exactly one of the UUIDs is non-null. */
    public record Resolved(UUID shopCustomerId, UUID buyerUserId) {}

    private final byte[] encKey;
    private final byte[] macKey;

    @Autowired
    public CustomerInsightKeys(MerchantProperties properties) {
        this(deriveMaster(properties));
    }

    /** Unit tests: pass a pre-derived master key (HMAC material). */
    static CustomerInsightKeys forTests(byte[] master) {
        return new CustomerInsightKeys(master);
    }

    private CustomerInsightKeys(byte[] master) {
        this.encKey = hmac(master, "enc".getBytes(StandardCharsets.UTF_8));
        this.macKey = hmac(master, "mac".getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] deriveMaster(MerchantProperties properties) {
        String custom = properties.customerKeySecret();
        String material = (custom != null && !custom.isBlank()) ? custom.trim() : properties.dataEncryptionSecret();
        if (material == null || material.isBlank()) {
            throw new IllegalStateException("customer key secret is not configured");
        }
        return hmac(material.getBytes(StandardCharsets.UTF_8), MASTER_INFO.getBytes(StandardCharsets.UTF_8));
    }

    public String encode(UUID shopUuid, UUID shopCustomerId, UUID buyerUserId) {
        if (shopUuid == null) throw new IllegalArgumentException("shopUuid required");
        boolean hasCustomer = shopCustomerId != null;
        boolean hasBuyer = buyerUserId != null;
        if (hasCustomer == hasBuyer) throw new IllegalArgumentException("exactly one of shopCustomerId or buyerUserId required");
        byte kind = hasCustomer ? KIND_CUSTOMER : KIND_BUYER;
        UUID id = hasCustomer ? shopCustomerId : buyerUserId;
        byte[] plain = new byte[PLAIN_LEN];
        plain[0] = kind;
        ByteBuffer.wrap(plain, 1, 16).put(uuidBytes(id));
        byte[] iv = Arrays.copyOf(hmac(macKey, ivMessage(shopUuid, kind, id)), IV_LEN);
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encKey, "AES"), new GCMParameterSpec(TAG_LEN * 8, iv));
            cipher.updateAAD(aad(shopUuid));
            byte[] ctAndTag = cipher.doFinal(plain);
            byte[] blob = new byte[BLOB_LEN];
            System.arraycopy(iv, 0, blob, 0, IV_LEN);
            System.arraycopy(ctAndTag, 0, blob, IV_LEN, PLAIN_LEN + TAG_LEN);
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(blob);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("customer key encode failed", e);
        }
    }

    public Optional<Resolved> resolve(UUID shopUuid, String key) {
        if (shopUuid == null || key == null || key.isBlank()) return Optional.empty();
        String trimmed = key.trim();
        if (!trimmed.startsWith(PREFIX)) return Optional.empty();
        byte[] blob;
        try {
            blob = Base64.getUrlDecoder().decode(trimmed.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (blob.length != BLOB_LEN) return Optional.empty();
        byte[] iv = Arrays.copyOfRange(blob, 0, IV_LEN);
        byte[] ctAndTag = Arrays.copyOfRange(blob, IV_LEN, BLOB_LEN);
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encKey, "AES"), new GCMParameterSpec(TAG_LEN * 8, iv));
            cipher.updateAAD(aad(shopUuid));
            byte[] plain = cipher.doFinal(ctAndTag);
            if (plain.length != PLAIN_LEN) return Optional.empty();
            byte kind = plain[0];
            UUID id = uuidFrom(Arrays.copyOfRange(plain, 1, PLAIN_LEN));
            byte[] expectedIv = Arrays.copyOf(hmac(macKey, ivMessage(shopUuid, kind, id)), IV_LEN);
            if (!Arrays.equals(iv, expectedIv)) return Optional.empty();
            if (kind == KIND_CUSTOMER) return Optional.of(new Resolved(id, null));
            if (kind == KIND_BUYER) return Optional.of(new Resolved(null, id));
            return Optional.empty();
        } catch (GeneralSecurityException e) {
            return Optional.empty();
        }
    }

    static String toGkey(Resolved resolved) {
        if (resolved.shopCustomerId() != null) return resolved.shopCustomerId().toString();
        return "u:" + resolved.buyerUserId();
    }

    private static byte[] aad(UUID shopUuid) {
        return (AAD_PREFIX + shopUuid).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] ivMessage(UUID shopUuid, byte kind, UUID id) {
        byte[] shop = uuidBytes(shopUuid);
        byte[] idBytes = uuidBytes(id);
        byte[] msg = new byte[shop.length + 1 + 1 + idBytes.length];
        System.arraycopy(shop, 0, msg, 0, shop.length);
        msg[shop.length] = 0x00;
        msg[shop.length + 1] = kind;
        System.arraycopy(idBytes, 0, msg, shop.length + 2, idBytes.length);
        return msg;
    }

    private static byte[] uuidBytes(UUID id) {
        var buf = ByteBuffer.allocate(16);
        buf.putLong(id.getMostSignificantBits());
        buf.putLong(id.getLeastSignificantBits());
        return buf.array();
    }

    private static UUID uuidFrom(byte[] bytes) {
        var buf = ByteBuffer.wrap(bytes);
        return new UUID(buf.getLong(), buf.getLong());
    }

    private static byte[] hmac(byte[] key, byte[] message) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
