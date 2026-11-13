package com.thaishopfun.translation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslationJsonTest {

    @Test
    void myMemoryDecodesUnicodeEscapesForChinese() {
        var body = "{\"responseData\":{\"translatedText\":\"\\u624b\\u673a\",\"match\":0.99}}";
        assertEquals("手机", TranslationJson.myMemoryTranslatedText(body));
    }

    @Test
    void myMemoryKeepsPlainAscii() {
        var body = "{\"responseData\":{\"translatedText\":\"Mobile\",\"match\":0.99}}";
        assertEquals("Mobile", TranslationJson.myMemoryTranslatedText(body));
    }
}
