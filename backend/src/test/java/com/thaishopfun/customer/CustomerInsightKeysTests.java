package com.thaishopfun.customer;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CustomerInsightKeysTests {
    private final CustomerInsightKeys keys = newKeys("unit-test-customer-key-secret");

    @Test
    void roundTripCustomerAndBuyer() {
        var shop = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var buyerId = UUID.randomUUID();

        String cKey = keys.encode(shop, customerId, null);
        String uKey = keys.encode(shop, null, buyerId);
        assertTrue(cKey.startsWith("c1"));
        assertTrue(uKey.startsWith("c1"));
        assertNotEquals(cKey, uKey);

        var c = keys.resolve(shop, cKey).orElseThrow();
        assertEquals(customerId, c.shopCustomerId());
        assertNull(c.buyerUserId());

        var u = keys.resolve(shop, uKey).orElseThrow();
        assertNull(u.shopCustomerId());
        assertEquals(buyerId, u.buyerUserId());
    }

    @Test
    void deterministicAndShopBound() {
        var shopA = UUID.randomUUID();
        var shopB = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        String a1 = keys.encode(shopA, customerId, null);
        String a2 = keys.encode(shopA, customerId, null);
        String b = keys.encode(shopB, customerId, null);
        assertEquals(a1, a2);
        assertNotEquals(a1, b);
        assertTrue(keys.resolve(shopB, a1).isEmpty());
        assertTrue(keys.resolve(shopA, b).isEmpty());
    }

    @Test
    void rejectsLegacyAndTampered() {
        var shop = UUID.randomUUID();
        var buyer = UUID.randomUUID();
        String legacy = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(("u:" + buyer).getBytes(StandardCharsets.UTF_8));
        assertTrue(keys.resolve(shop, legacy).isEmpty());
        assertTrue(keys.resolve(shop, null).isEmpty());
        assertTrue(keys.resolve(shop, "").isEmpty());
        assertTrue(keys.resolve(shop, "not-a-key").isEmpty());

        String good = keys.encode(shop, UUID.randomUUID(), null);
        char[] chars = good.toCharArray();
        chars[chars.length - 1] = chars[chars.length - 1] == 'A' ? 'B' : 'A';
        assertTrue(keys.resolve(shop, new String(chars)).isEmpty());
    }

    @Test
    void plaintextDoesNotLeakIntoKey() {
        var shop = UUID.randomUUID();
        var buyer = UUID.randomUUID();
        String key = keys.encode(shop, null, buyer);
        assertFalse(key.contains(buyer.toString()));
        assertFalse(key.contains(buyer.toString().replace("-", "")));
        byte[] blob = Base64.getUrlDecoder().decode(key.substring(2));
        String asText = new String(blob, StandardCharsets.ISO_8859_1);
        assertFalse(asText.contains("u:"));
        assertFalse(asText.contains(buyer.toString()));
    }

    private static CustomerInsightKeys newKeys(String secret) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] master = mac.doFinal("tsf/customer-key/v1".getBytes(StandardCharsets.UTF_8));
            return CustomerInsightKeys.forTests(master);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
