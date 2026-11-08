package com.thaishopfun.merchant;

import com.thaishopfun.auth.AuthException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShopChannelUrlsTest {
    @Test
    void normalizesHandlesAndHttpsHosts() {
        var channels = ShopChannelUrls.normalizeOrThrow(
            "example.com/shop",
            "@tinzshop",
            "facebook.com/tinz",
            "@tinz",
            "https://www.tiktok.com/@tinz"
        );
        assertEquals("https://example.com/shop", channels.website());
        assertEquals("@tinzshop", channels.lineId());
        assertEquals("https://facebook.com/tinz", channels.facebookUrl());
        assertEquals("https://www.instagram.com/tinz", channels.instagramUrl());
        assertEquals("https://www.tiktok.com/@tinz", channels.tiktokUrl());
    }

    @Test
    void rejectsFakeHostsAndDangerousSchemes() {
        assertThrows(AuthException.class, () -> ShopChannelUrls.normalizeOrThrow(null, null, "https://facebook.evil.com/x", null, null));
        assertThrows(AuthException.class, () -> ShopChannelUrls.normalizeOrThrow("javascript:alert(1)", null, null, null, null));
        assertThrows(AuthException.class, () -> ShopChannelUrls.normalizeOrThrow("https://user:pass@example.com", null, null, null, null));
    }

    @Test
    void clearsBlankFields() {
        var channels = ShopChannelUrls.normalizeOrThrow("  ", "", null, "   ", null);
        assertNull(channels.website());
        assertNull(channels.lineId());
        assertNull(channels.facebookUrl());
        assertNull(channels.instagramUrl());
        assertNull(channels.tiktokUrl());
    }
}
