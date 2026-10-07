package com.thaishopfun.translation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.translation.provider", havingValue = "fake")
public class FakeTranslationProvider implements TranslationProvider {
    @Override
    public String id() {
        return "fake";
    }

    @Override
    public List<String> translate(List<String> texts, String from, String to) {
        var prefix = to.startsWith("zh") ? "ZH:" : "EN:";
        return texts.stream().map(text -> text == null || text.isBlank() ? text : prefix + text).toList();
    }
}
