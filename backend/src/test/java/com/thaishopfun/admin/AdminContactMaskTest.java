package com.thaishopfun.admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminContactMaskTest {

    @Test
    void phoneNormalizesPlus66AndMasksLastFour() {
        assertEquals("08x-xxx-5678", AdminContactMask.phone("+66812345678"));
        assertEquals("08x-xxx-5678", AdminContactMask.phone("66812345678"));
        assertEquals("08x-xxx-5678", AdminContactMask.phone("0812345678"));
    }

    @Test
    void phoneNullBlankAndShort() {
        assertNull(AdminContactMask.phone(null));
        assertEquals("", AdminContactMask.phone(""));
        assertEquals("   ", AdminContactMask.phone("   "));
        assertEquals("****", AdminContactMask.phone("12"));
        assertEquals("****", AdminContactMask.phone("ab"));
    }

    @Test
    void emailMasksLocalPartAndLeavesXInLocalAlone() {
        assertEquals("x***@example.com", AdminContactMask.email("xavier@example.com"));
        assertEquals("s***@example.com", AdminContactMask.email("somchai@example.com"));
        assertEquals("*@ex.com", AdminContactMask.email("a@ex.com"));
    }

    @Test
    void emailNullBlankInvalid() {
        assertNull(AdminContactMask.email(null));
        assertEquals("", AdminContactMask.email(""));
        assertEquals("***", AdminContactMask.email("not-an-email"));
    }

    @Test
    void normalizePhoneDigitsConverts66() {
        assertEquals("0812345678", AdminContactMask.normalizePhoneDigits("+66812345678"));
        assertEquals("0812345678", AdminContactMask.normalizePhoneDigits("66-812-345-678"));
        assertEquals("0812345678", AdminContactMask.normalizePhoneDigits("081-234-5678"));
    }
}
