package com.thaishopfun.translation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class NoopTranslationProvider implements TranslationProvider {
    @Override
    public String id() {
        return "none";
    }

    @Override
    public List<String> translate(List<String> texts, String from, String to) {
        return new ArrayList<>(texts);
    }
}
