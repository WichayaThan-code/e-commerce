package com.thaishopfun.translation;

import java.util.List;

public interface TranslationProvider {
    String id();

    List<String> translate(List<String> texts, String from, String to);

    default boolean configured() {
        return true;
    }
}
