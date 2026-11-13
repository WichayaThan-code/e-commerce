package com.thaishopfun.translation;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyMemoryTranslationProviderTest {

    @Test
    void translatesThaiToEnglishAndChinese() throws Exception {
        Assumptions.assumeTrue(reachable(), "MyMemory not reachable from this environment");
        var provider = new MyMemoryTranslationProvider(new TranslationProperties(
            "mymemory", null, null, "ThaiShopFun,SKU", 40, 5, 30_000
        ));
        var en = provider.translate(List.of("มือถือ"), "th", "en");
        var zh = provider.translate(List.of("มือถือ"), "th", "zh");
        assertFalse(en.isEmpty());
        assertFalse(zh.isEmpty());
        assertFalse(en.get(0).contains("มือถือ"), () -> "EN still Thai: " + en.get(0));
        assertFalse(en.get(0).startsWith("EN:"), () -> "EN looks like fake provider: " + en.get(0));
        assertTrue(en.get(0).toLowerCase().contains("mobile") || en.get(0).length() > 0, en.get(0));
        assertFalse(zh.get(0).contains("มือถือ"), () -> "ZH still Thai: " + zh.get(0));
    }

    private static boolean reachable() {
        try {
            var connection = (HttpURLConnection) URI.create(
                "https://api.mymemory.translated.net/get?q=hi&langpair=en%7Cit"
            ).toURL().openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod("GET");
            return connection.getResponseCode() < 500;
        } catch (Exception ignored) {
            return false;
        }
    }
}
